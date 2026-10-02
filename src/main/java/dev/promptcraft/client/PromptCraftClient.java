package dev.promptcraft.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.promptcraft.client.gui.PlacementConfirmScreen;
import dev.promptcraft.client.gui.PromptCraftSettingsScreen;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftLang;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.structure.PromptCraftStructure;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class PromptCraftClient implements ClientModInitializer {
    private static BlockPos firstPos = null;
    private static BlockPos secondPos = null;

    public static BlockPos getFirstPos() { return firstPos; }
    public static BlockPos getSecondPos() { return secondPos; }
    public static boolean hasCompleteSelection() { return firstPos != null && secondPos != null; }

    private static boolean scrollHookInstalled = false;

    private static KeyBinding rotateGhostKey;
    private static KeyBinding confirmPlacementKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(dev.promptcraft.PromptCraftMod.MOD_ID, "main"));

        KeyBinding openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.promptcraft.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        ));

        rotateGhostKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.promptcraft.rotate_ghost",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category
        ));

        confirmPlacementKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.promptcraft.confirm_placement",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                category
        ));

        // При заходе в мир подхватываем язык интерфейса Minecraft: русский -> ru,
        // любой другой -> en. Одна проверка на join, нагрузки нет.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String mcLang = client.getLanguageManager().getLanguage();
            String detected = (mcLang != null && mcLang.toLowerCase().startsWith("ru")) ? "ru" : "en";
            if (!detected.equals(PromptCraftConfigManager.get().language)) {
                PromptCraftConfigManager.get().language = detected;
                PromptCraftConfigManager.save();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            installScrollHookIfNeeded(client);

            while (openMenuKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientPlayNetworking.send(new dev.promptcraft.network.PromptCraftPayloads.RequestOpenGuiPayload());
                }
            }

            while (rotateGhostKey.wasPressed()) {
                if (GhostPreviewState.isActive()) {
                    GhostPreviewState.rotate();
                }
            }

            while (confirmPlacementKey.wasPressed()) {
                if (GhostPreviewState.isActive() && client.currentScreen == null) {
                    BlockPos anchor = GhostPreviewState.getCurrentAnchor();
                    int rotation = GhostPreviewState.getRotationSteps();
                    client.setScreen(new PlacementConfirmScreen(anchor, rotation));
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.promptcraft.network.PromptCraftPayloads.SelectionSyncPayload.ID, (payload, context) -> {
            BlockPos first = payload.hasFirst() ? payload.first() : null;
            BlockPos second = payload.hasSecond() ? payload.second() : null;
            context.client().execute(() -> { firstPos = first; secondPos = second; });
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.promptcraft.network.PromptCraftPayloads.AiStreamPayload.ID, (payload, context) -> {
            String eventType = payload.eventType();
            String pData = payload.payload();
            context.client().execute(() -> {
                switch (eventType) {
                    case "start" -> AiStreamState.reset();
                    case "reasoning" -> AiStreamState.append(pData);
                    case "done" -> AiStreamState.finish();
                    case "error" -> AiStreamState.fail(pData);
                    case "cancelled" -> AiStreamState.cancelled();
                    default -> {}
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.promptcraft.network.PromptCraftPayloads.StartFreePlacementPayload.ID, (payload, context) -> {
            String json = payload.structureJson();
            context.client().execute(() -> {
                try {
                    PromptCraftStructure structure = new Gson().fromJson(json, PromptCraftStructure.class);
                    GhostPreviewState.start(structure);
                } catch (Exception ignored) {
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.promptcraft.network.PromptCraftPayloads.CancelFreePlacementPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                GhostPreviewState.cancel();
                if (context.client().currentScreen instanceof PlacementConfirmScreen) {
                    context.client().setScreen(null);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.promptcraft.network.PromptCraftPayloads.BuildProgressPayload.ID, (payload, context) -> {
            int percent = payload.percent();
            boolean active = payload.active();
            context.client().execute(() -> {
                if (active) {
                    BuildProgressState.update(percent);
                } else if (percent >= 100) {
                    BuildProgressState.complete();
                } else {
                    BuildProgressState.hide();
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(dev.promptcraft.network.PromptCraftPayloads.OpenGuiPayload.ID, (payload, context) -> {
            context.client().execute(() -> context.client().setScreen(
                    new PromptCraftSettingsScreen(
                            payload.provider(),
                            payload.apiKeys(),
                            payload.model(),
                            payload.showPreview(),
                            payload.language(),
                            payload.themeColor(),
                            payload.thickOutline(),
                            payload.fillOpacity(),
                            payload.outlineThroughBlocks(),
                            payload.selectionLimitEnabled(),
                            payload.maxSelectionWidth(),
                            payload.maxSelectionHeight(),
                            payload.maxSelectionDepth(),
                            payload.proceduralTexturing()
                    )
            ));
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!GhostPreviewState.isActive()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen != null) return;

            String keyName = rotateGhostKey.getBoundKeyLocalizedText().getString();
            String hint = PromptCraftLang.t("Press ", "Нажмите ")
                + keyName
                + PromptCraftLang.t(" to rotate the structure", ", чтобы повернуть структуру");

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            int textWidth = client.textRenderer.getWidth(hint);
            int x = (screenWidth - textWidth) / 2;
            int y = screenHeight - 40;

            drawContext.drawTextWithShadow(client.textRenderer, hint, x, y, 0xFFFFFF);
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!BuildProgressState.isVisible()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen != null) return;

            int percent = BuildProgressState.getPercent();
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            int barWidth = 160;
            int barHeight = 9;
            int barX = (screenWidth - barWidth) / 2;
            int barY = 20;

            String hex = PromptCraftConfigManager.get().themeColor.replace("#", "");
            int rgb = 0x17b95f;
            try { rgb = Integer.parseInt(hex, 16); } catch (Exception ignored) {}
            int fillColor = 0xFF000000 | rgb;

            String label = PromptCraftLang.t("Building structure", "Строительство структуры");
            drawContext.drawTextWithShadow(client.textRenderer, label, barX, barY + barHeight + 4, 0xFFFFFFFF);

            // рамка + фон трека
            drawContext.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
            drawContext.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF2B2B2B);

            // заполнение
            int fillW = Math.max(0, Math.min(barWidth, (int) (barWidth * (percent / 100.0f))));
            drawContext.fill(barX, barY, barX + fillW, barY + barHeight, fillColor);

            // проценты справа
            String pct = percent + "%";
            drawContext.drawTextWithShadow(client.textRenderer, pct, barX + barWidth + 6, barY + 1, 0xFFFFFFFF);
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            GhostRenderer.render(context);

            boolean showPreview = PromptCraftConfigManager.get().showSelectionPreview;
            if (!showPreview) return;

            BlockPos pos1 = firstPos;
            BlockPos pos2 = secondPos;

            if (pos1 != null && pos2 == null) {
                boolean holdingBrush = client.player.getMainHandStack().isOf(dev.promptcraft.PromptCraftItems.SELECTION_BRUSH) ||
                                       client.player.getOffHandStack().isOf(dev.promptcraft.PromptCraftItems.SELECTION_BRUSH);

                if (!holdingBrush) return;

                HitResult hit = client.crosshairTarget;
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    pos2 = ((BlockHitResult) hit).getBlockPos();
                } else {
                    float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                    Vec3d eyePos = client.player.getCameraPosVec(tickDelta);
                    Vec3d lookVec = client.player.getRotationVec(tickDelta);
                    pos2 = BlockPos.ofFloored(eyePos.add(lookVec.multiply(5.0D)));
                }
            }

            if (pos1 == null || pos2 == null) return;

            String hex = PromptCraftConfigManager.get().themeColor.replace("#", "");
            int color = 0x17b95f;
            try { color = Integer.parseInt(hex, 16); } catch (Exception ignored) {}
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            net.minecraft.client.util.math.MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();

            int minX = Math.min(pos1.getX(), pos2.getX());
            int minY = Math.min(pos1.getY(), pos2.getY());
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
            int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

            double fillEpsilon = 0.025D;
            double outlineEpsilon = 0.018D;

            Box fillBox = new Box(
                    minX - fillEpsilon,
                    minY + 0.006D,
                    minZ - fillEpsilon,
                    maxX + fillEpsilon,
                    maxY + fillEpsilon,
                    maxZ + fillEpsilon
            ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            Box outlineBox = new Box(
                    minX - outlineEpsilon,
                    minY + 0.012D,
                    minZ - outlineEpsilon,
                    maxX + outlineEpsilon,
                    maxY + outlineEpsilon,
                    maxZ + outlineEpsilon
            ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            PromptCraftConfig config = PromptCraftConfigManager.get();
            float fillOpacity = Math.max(0.0f, Math.min(1.0f, config.selectionFillOpacity));

            if (fillOpacity > 0.0f) {
                VertexConsumer fillConsumer = consumers.getBuffer(RenderLayer.getDebugFilledBox());
                VertexRendering.drawFilledBox(matrices, fillConsumer, fillBox.minX, fillBox.minY, fillBox.minZ, fillBox.maxX, fillBox.maxY, fillBox.maxZ, r, g, b, fillOpacity);
            }

            VertexConsumer lineConsumer = consumers.getBuffer(RenderLayer.getLines());
            VertexRendering.drawBox(matrices.peek(), lineConsumer, outlineBox, r, g, b, 1.0f);
        });
    }

    private static void installScrollHookIfNeeded(MinecraftClient client) {
        if (scrollHookInstalled || client.getWindow() == null) return;
        long handle = client.getWindow().getHandle();

        // Чейнимся к прежнему колбэку Minecraft, чтобы вне режима предпросмотра
        // колесо работало штатно (хотбар и т.д.).
        org.lwjgl.glfw.GLFWScrollCallbackI[] previous = new org.lwjgl.glfw.GLFWScrollCallbackI[1];
        org.lwjgl.glfw.GLFWScrollCallback ours = org.lwjgl.glfw.GLFWScrollCallback.create((win, dx, dy) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (GhostPreviewState.isActive() && mc.currentScreen == null) {
                GhostPreviewState.addDistance(dy * 2.0); // вверх = дальше, вниз = ближе
                return; // не листаем хотбар во время позиционирования
            }
            if (previous[0] != null) previous[0].invoke(win, dx, dy);
        });

        previous[0] = org.lwjgl.glfw.GLFW.glfwSetScrollCallback(handle, ours);
        scrollHookInstalled = true;
    }
}