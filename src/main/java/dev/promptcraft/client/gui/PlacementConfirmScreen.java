package dev.promptcraft.client.gui;

import dev.promptcraft.client.GhostPreviewState;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.network.PromptCraftNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class PlacementConfirmScreen extends Screen {
    private final BlockPos anchor;
    private final int rotationSteps;
    private final String language;

    // Геометрия оверлея (как в окне выбора языка).
    private static final int OVERLAY_W = 320;
    private static final int OVERLAY_H = 120;

    public PlacementConfirmScreen(BlockPos anchor, int rotationSteps) {
        super(Text.literal("Confirm Placement"));
        this.anchor = anchor;
        this.rotationSteps = rotationSteps;
        this.language = PromptCraftConfigManager.get().language;
    }

    private String t(String en, String ru) {
        return "ru".equals(language) ? ru : en;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void confirm() {
        ClientPlayNetworking.send(new dev.promptcraft.network.PromptCraftPayloads.ConfirmPlacementPayload(anchor, rotationSteps));
        GhostPreviewState.cancel();
        if (this.client != null) this.client.setScreen(null);
    }

    private void cancel() {
        // Возврат к позиционированию призрака, на сервер ничего не шлём.
        if (this.client != null) this.client.setScreen(null);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { cancel(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        int ox = (this.width - OVERLAY_W) / 2;
        int oy = (this.height - OVERLAY_H) / 2;

        // Крестик закрытия = отмена.
        int closeX = ox + OVERLAY_W - 22;
        int closeY = oy + 5;
        double mouseX = click.x();
        double mouseY = click.y();
        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 14) {
            cancel();
            return true;
        }

        int[] yes = yesRect(ox, oy);
        int[] no = noRect(ox, oy);
        if (inRect(mouseX, mouseY, yes)) { confirm(); return true; }
        if (inRect(mouseX, mouseY, no)) { cancel(); return true; }

        return super.mouseClicked(click, bl);
    }

    private int[] yesRect(int ox, int oy) {
        return new int[]{ox + 14, oy + OVERLAY_H - 34, 135, 22};
    }

    private int[] noRect(int ox, int oy) {
        return new int[]{ox + OVERLAY_W - 14 - 135, oy + OVERLAY_H - 34, 135, 22};
    }

    private boolean inRect(double mx, double my, int[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int ox = (this.width - OVERLAY_W) / 2;
        int oy = (this.height - OVERLAY_H) / 2;

        // Тот же базовый оверлей, что и у меню выбора языка.
        context.fill(0, 0, this.width, this.height, 0x80000000);
        context.fill(ox, oy, ox + OVERLAY_W, oy + OVERLAY_H, 0xFF1E1E1E);
        context.fill(ox, oy, ox + OVERLAY_W, oy + 1, 0xFF555555);
        context.fill(ox, oy + OVERLAY_H - 1, ox + OVERLAY_W, oy + OVERLAY_H, 0xFF111111);
        context.fill(ox, oy, ox + 1, oy + OVERLAY_H, 0xFF555555);
        context.fill(ox + OVERLAY_W - 1, oy, ox + OVERLAY_W, oy + OVERLAY_H, 0xFF111111);

        context.drawTextWithShadow(this.textRenderer,
            t("Confirm Placement", "Подтверждение размещения"), ox + 10, oy + 8, 0xFFFFFF);

        // Крестик закрытия.
        int closeX = ox + OVERLAY_W - 22;
        int closeY = oy + 5;
        context.fill(closeX, closeY, closeX + 18, closeY + 14, 0xFF2D2D2D);
        context.drawText(this.textRenderer, "X", closeX + 6, closeY + 3, 0xFFAAAAAA, false);

        // Вопрос.
        String question = t("Are you sure you want to place the structure here?",
                            "Вы уверены, что хотите разместить структуру здесь?");
        context.drawCenteredTextWithShadow(this.textRenderer, question,
            ox + OVERLAY_W / 2, oy + 40, 0xD2D2D2);

        // Кнопки.
        int[] yes = yesRect(ox, oy);
        int[] no = noRect(ox, oy);
        drawButton(context, yes, mouseX, mouseY, t("Yes", "Да"), 0xFF2E7D32, 0xFF43B14B);
        drawButton(context, no, mouseX, mouseY, t("No", "Нет"), 0xFF8B2E2E, 0xFFB1433F);
    }

    private void drawButton(DrawContext context, int[] r, int mouseX, int mouseY, String label, int base, int hover) {
        boolean hovered = inRect(mouseX, mouseY, r);
        context.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hovered ? hover : base);
        int textX = r[0] + (r[2] - this.textRenderer.getWidth(label)) / 2;
        int textY = r[1] + (r[3] - 8) / 2;
        context.drawTextWithShadow(this.textRenderer, label, textX, textY, 0xFFFFFFFF);
    }
}