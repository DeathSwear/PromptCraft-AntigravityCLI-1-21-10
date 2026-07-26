package dev.promptcraft.client.gui.widget;

import dev.promptcraft.client.gui.GuiColors;
import dev.promptcraft.client.gui.GuiPainter;
import dev.promptcraft.client.gui.SettingsContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class OpacitySlider extends SliderWidget {

    private final SettingsContext ctx;

    public OpacitySlider(SettingsContext ctx, int x, int y, int width, int height, float initialValue) {
        super(x, y, width, height, Text.empty(), Math.max(0.0D, Math.min(1.0D, initialValue)));
        this.ctx = ctx;
        updateMessage();
    }

    /** public, а не protected: экран пересобирает подпись при смене языка. */
    @Override
    public void updateMessage() {
        if (ctx == null) return; // вызов из конструктора super() до присвоения поля
        int percent = (int) Math.round(this.value * 100.0D);
        this.setMessage(Text.literal(ctx.t("Opacity: ", "Прозрачность: ") + percent + "%"));
    }

    @Override
    protected void applyValue() {
        if (ctx == null) return;
        ctx.state().setFillOpacity((float) this.value);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;

        int bgColor = this.isHovered() ? GuiPainter.COLOR_SLOT_HOVER : GuiPainter.COLOR_SLOT;
        int themeColorInt = ctx.themeColorArgb();

        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

        int fillW = (int) (this.width * this.value);
        context.fill(this.getX(), this.getY(), this.getX() + fillW, this.getY() + this.height, themeColorInt);

        int textX = this.getX() + (this.width - ctx.textRenderer().getWidth(this.getMessage())) / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        context.drawTextWithShadow(ctx.textRenderer(), this.getMessage(), textX, textY,
                GuiColors.isLight((float) this.value) ? 0x000000 : 0xFFFFFF);
    }
}