package dev.promptcraft.network;

import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.config.PromptCraftLang;
import dev.promptcraft.selection.PlayerSelection;
import dev.promptcraft.session.GenerationSession;
import dev.promptcraft.session.PendingPrompt;
import dev.promptcraft.session.PromptSessionManager;
import dev.promptcraft.structure.PromptCraftStructure;
import dev.promptcraft.structure.StructureRotationUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class PromptCraftNetworking {

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(PromptCraftPayloads.SaveGuiPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (!dev.promptcraft.PromptCraftCommands.hasAccess(player)) return;

                PromptCraftEnv.saveApiKeys(payload.apiKeys());
                PromptCraftConfig config = PromptCraftConfigManager.get();
                config.provider = payload.provider();
                config.model = payload.model();
                config.showSelectionPreview = payload.showPreview();
                config.language = payload.language();
                config.themeColor = payload.themeColor();
                config.thickSelectionOutline = payload.thickOutline();
                config.selectionFillOpacity = Math.max(0.0f, Math.min(1.0f, payload.fillOpacity()));
                config.selectionOutlineThroughBlocks = payload.outlineThroughBlocks();

                config.selectionLimitEnabled = payload.selectionLimitEnabled();
                config.maxSelectionWidth = Math.max(1, payload.maxSelectionWidth());
                config.maxSelectionHeight = Math.max(1, payload.maxSelectionHeight());
                config.maxSelectionDepth = Math.max(1, payload.maxSelectionDepth());

                PromptCraftConfigManager.save();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PromptCraftPayloads.RequestOpenGuiPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (dev.promptcraft.PromptCraftCommands.hasAccess(player)) {
                    openSettingsGui(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PromptCraftPayloads.GuiActionPayload.ID, (payload, context) -> {
            String action = payload.action();
            String promptText = payload.promptText();

            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                boolean isGenOrEdit = "generate".equals(action) || "edit".equals(action);

                if (!dev.promptcraft.PromptCraftCommands.hasAccess(player)) {
                    if (isGenOrEdit) sendAiStreamEvent(player, "cancelled", "");
                    return;
                }
                if (!player.isCreative()) {
                    player.sendMessage(Text.literal(PromptCraftLang.t("You must be in creative mode.", "Нужно находиться в творческом режиме.")).formatted(Formatting.RED), false);
                    if (isGenOrEdit) sendAiStreamEvent(player, "cancelled", "");
                    return;
                }

                if ("generate".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("A generation is already in progress.", "Генерация уже выполняется.")).formatted(Formatting.RED), false);
                        return;
                    }

                    PromptCraftConfig config = PromptCraftConfigManager.get();
                    boolean isFreeMode = "free".equals(config.generationMode);

                    if (isFreeMode) {
                        PendingPrompt prompt = new PendingPrompt(promptText, BlockPos.ORIGIN, BlockPos.ORIGIN, 0, 0, 0);
                        PromptSessionManager.setLast(player, prompt);
                        executeFreeBuildProcess(player, prompt);
                    } else {
                        PlayerSelection selection = dev.promptcraft.selection.SelectionManager.get(player);
                        if (!selection.isComplete()) {
                            player.sendMessage(Text.literal(PromptCraftLang.t("You must select an area first!", "Сначала нужно выделить область!")).formatted(Formatting.RED), false);
                            sendAiStreamEvent(player, "cancelled", "");
                            return;
                        }

                        PendingPrompt prompt = new PendingPrompt(promptText, selection.getMin(), selection.getMax(), selection.getWidth(), selection.getHeight(), selection.getDepth());
                        PromptSessionManager.setLast(player, prompt);
                        executeBuildProcess(player, prompt);
                    }

                } else if ("edit".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("A generation is already in progress.", "Генерация уже выполняется.")).formatted(Formatting.RED), false);
                        sendAiStreamEvent(player, "cancelled", "");
                        return;
                    }

                    var lastOpt = PromptSessionManager.getLast(player);
                    if (lastOpt.isEmpty()) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("No previous prompt to edit!", "Нет предыдущего запроса для правки!")).formatted(Formatting.RED), false);
                        sendAiStreamEvent(player, "cancelled", "");
                        return;
                    }
                    PendingPrompt last = lastOpt.get();
                    String combined = "Original request: " + last.getPrompt() + ". User edit request: " + promptText + ". Please modify the design accordingly.";
                    PendingPrompt newPrompt = new PendingPrompt(combined, last.getSelectionMin(), last.getSelectionMax(), last.getWidth(), last.getHeight(), last.getDepth());
                    PromptSessionManager.setLast(player, newPrompt);
                    executeBuildProcess(player, newPrompt);

                } else if ("undo".equals(action)) {
                    var activeSession = PromptSessionManager.getActiveGeneration(player);
                    if (activeSession.isPresent() && !activeSession.get().isCancelled()) {
                        cancelGeneration(player, activeSession.get());
                        return;
                    }

                    if (dev.promptcraft.structure.HistoryManager.undo(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Step back successful.", "Шаг назад выполнен.")).formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Nothing to undo.", "Нечего отменять.")).formatted(Formatting.RED), false);
                    }
                } else if ("back".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Cannot go back while generating.", "Нельзя вернуться назад во время генерации.")).formatted(Formatting.RED), false);
                        return;
                    }

                    if (dev.promptcraft.structure.HistoryManager.undo(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Step back successful.", "Шаг назад выполнен.")).formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Nothing to undo.", "Нечего отменять.")).formatted(Formatting.RED), false);
                    }
                } else if ("next".equals(action)) {
                    if (PromptSessionManager.isGenerating(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Cannot redo while generating.", "Нельзя вернуть во время генерации.")).formatted(Formatting.RED), false);
                        return;
                    }

                    if (dev.promptcraft.structure.HistoryManager.redo(player)) {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Step forward successful.", "Шаг вперёд выполнен.")).formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal(PromptCraftLang.t("Nothing to redo.", "Нечего возвращать.")).formatted(Formatting.RED), false);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PromptCraftPayloads.ConfirmPlacementPayload.ID, (payload, context) -> {
            BlockPos anchor = payload.anchor();
            int rotationSteps = payload.rotationSteps();

            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (!dev.promptcraft.PromptCraftCommands.hasAccess(player) || !player.isCreative()) return;

                var sessionOpt = PromptSessionManager.getActiveGeneration(player);
                if (sessionOpt.isEmpty() || !sessionOpt.get().isGhostPending()) return;

                GenerationSession session = sessionOpt.get();
                PromptCraftStructure structure = session.getPendingStructure();

                if (structure == null) {
                    PromptSessionManager.clearGeneration(player);
                    return;
                }

                PromptCraftStructure rotated = StructureRotationUtil.rotate(structure, rotationSteps);
                PromptCraftStructure.Bounds bounds = rotated.computeBounds();

                BlockPos min = anchor.add(bounds.minX(), bounds.minY(), bounds.minZ());
                BlockPos max = anchor.add(bounds.maxX(), bounds.maxY(), bounds.maxZ());

                session.setGhostPending(false);
                session.setPendingStructure(null);

                PromptSessionManager.getLast(player).ifPresent(last -> {
                    PendingPrompt updated = new PendingPrompt(
                            last.getPrompt(), min, max, bounds.width(), bounds.height(), bounds.depth()
                    );
                    PromptSessionManager.setLast(player, updated);
                });

                player.sendMessage(Text.literal(PromptCraftLang.t("Placing structure...", "Размещение структуры...")).formatted(Formatting.YELLOW), false);

                dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.DestructionTask(player, min, max, session, (destroyedSnapshots) -> {
                    if (session.isCancelled()) return;
                    player.sendMessage(Text.literal(PromptCraftLang.t("Area cleared. Building...", "Область очищена. Строим...")).formatted(Formatting.GREEN), false);
                    dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.BuildTask(player, anchor, rotated, session, destroyedSnapshots));
                }));
            });
        });
    }

    public static void syncSelection(ServerPlayerEntity player, PlayerSelection selection) {
        ServerPlayNetworking.send(player, new PromptCraftPayloads.SelectionSyncPayload(
                selection.hasFirst(), selection.getFirst(),
                selection.hasSecond(), selection.getSecond()
        ));
    }

    public static void openSettingsGui(ServerPlayerEntity player) {
        PromptCraftConfig config = PromptCraftConfigManager.get();
        Map<String, String> keys = PromptCraftEnv.getAllApiKeys();

        ServerPlayNetworking.send(player, new PromptCraftPayloads.OpenGuiPayload(
                config.provider, keys, config.model, config.showSelectionPreview,
                config.language, config.themeColor, config.thickSelectionOutline,
                config.selectionFillOpacity, config.selectionOutlineThroughBlocks,
                config.selectionLimitEnabled, config.maxSelectionWidth,
                config.maxSelectionHeight, config.maxSelectionDepth
        ));
    }

    public static void sendAiStreamEvent(ServerPlayerEntity player, String eventType, String payload) {
        ServerPlayNetworking.send(player, new PromptCraftPayloads.AiStreamPayload(eventType, payload));
    }

    public static void sendBuildProgress(ServerPlayerEntity player, int percent, boolean active) {
        ServerPlayNetworking.send(player, new PromptCraftPayloads.BuildProgressPayload(percent, active));
    }

    private static void executeBuildProcess(ServerPlayerEntity player, PendingPrompt prompt) {
        GenerationSession session = PromptSessionManager.startGeneration(player);

        player.sendMessage(Text.literal(PromptCraftLang.t("Preparing area...", "Подготовка области...")).formatted(Formatting.YELLOW), false);
        dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.DestructionTask(player, prompt.getSelectionMin(), prompt.getSelectionMax(), session, (destroyedSnapshots) -> {
            if (session.isCancelled()) return;

            player.sendMessage(Text.literal(PromptCraftLang.t("Area cleared. Contacting AI...", "Область очищена. Связь с ИИ...")).formatted(Formatting.AQUA), false);
            dev.promptcraft.ai.AiClient.requestBuild(player, prompt.getPrompt(), prompt.getWidth(), prompt.getHeight(), prompt.getDepth(), session)
                    .thenAccept(structure -> {
                        if (session.isCancelled()) return;

                        if (structure != null && player.getEntityWorld().getServer() != null) {
                            player.getEntityWorld().getServer().execute(() -> {
                                if (session.isCancelled()) return;
                                player.sendMessage(Text.literal(PromptCraftLang.t("AI response received! Building...", "Ответ ИИ получен! Строим...")).formatted(Formatting.GREEN), false);
                                dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.BuildTask(player, prompt.getSelectionMin(), structure, session, destroyedSnapshots));
                            });
                        } else {
                            if (destroyedSnapshots != null && !destroyedSnapshots.isEmpty() && player.getEntityWorld().getServer() != null) {
                                player.getEntityWorld().getServer().execute(() -> {
                                    dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.RestoreTask(player, destroyedSnapshots, null));
                                });
                            }
                            PromptSessionManager.clearGeneration(player);
                        }
                    });
        }));
    }

    private static void executeFreeBuildProcess(ServerPlayerEntity player, PendingPrompt prompt) {
        GenerationSession session = PromptSessionManager.startGeneration(player);
        PromptCraftConfig config = PromptCraftConfigManager.get();

        player.sendMessage(Text.literal(PromptCraftLang.t("Contacting AI for free placement...", "Связь с ИИ для свободного размещения...")).formatted(Formatting.AQUA), false);

        dev.promptcraft.ai.AiClient.requestFreeBuild(
                player,
                prompt.getPrompt(),
                config.selectionLimitEnabled,
                config.maxSelectionWidth,
                config.maxSelectionHeight,
                config.maxSelectionDepth,
                session
        ).thenAccept(structure -> {
            if (session.isCancelled()) return;

            if (structure != null && structure.operations != null && !structure.operations.isEmpty() && player.getEntityWorld().getServer() != null) {
                player.getEntityWorld().getServer().execute(() -> {
                    if (session.isCancelled()) return;

                    session.setPendingStructure(structure);
                    session.setGhostPending(true);

                    player.sendMessage(Text.literal(PromptCraftLang.t("AI response received! Position the preview and confirm placement.", "Ответ ИИ получен! Наведите предпросмотр и подтвердите размещение.")).formatted(Formatting.GREEN), false);

                    ServerPlayNetworking.send(player, new PromptCraftPayloads.StartFreePlacementPayload(new com.google.gson.Gson().toJson(structure)));
                });
            } else {
                if (player.getEntityWorld().getServer() != null) {
                    player.getEntityWorld().getServer().execute(() -> {
                        if (!session.isCancelled()) {
                            player.sendMessage(Text.literal(PromptCraftLang.t("AI returned an empty structure.", "ИИ вернул пустую структуру.")).formatted(Formatting.RED), false);
                        }
                        PromptSessionManager.clearGeneration(player);
                    });
                } else {
                    PromptSessionManager.clearGeneration(player);
                }
            }
        });
    }

    private static void cancelGeneration(ServerPlayerEntity player, GenerationSession session) {
        session.cancel();
        session.abortHttpRequest();

        if (session.isGhostPending()) {
            session.setGhostPending(false);
            session.setPendingStructure(null);
            ServerPlayNetworking.send(player, new PromptCraftPayloads.CancelFreePlacementPayload());
        } else if (session.isDestructionComplete()) {
            dev.promptcraft.structure.HistoryManager.undo(player);
        }

        PromptSessionManager.clearGeneration(player);
        sendAiStreamEvent(player, "cancelled", "");
        player.sendMessage(Text.literal(PromptCraftLang.t("Generation cancelled.", "Генерация отменена.")).formatted(Formatting.YELLOW), false);
    }
}