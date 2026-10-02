package dev.promptcraft.client.gui.widget;

import dev.promptcraft.client.gui.GuiPainter;
import dev.promptcraft.client.gui.SettingsContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class FlatButton extends ButtonWidget {

    protected final SettingsContext ctx;

    public FlatButton(SettingsContext ctx, int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.ctx = ctx;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        boolean enabled = this.active;
        int bgColor = !enabled
                ? GuiPainter.COLOR_SLOT_DISABLED
                : (this.isHovered() ? GuiPainter.COLOR_SLOT_HOVER : GuiPainter.COLOR_SLOT);
        int textColor = !enabled
                ? GuiPainter.COLOR_TEXT_DISABLED
                : (this.isHovered() ? GuiPainter.COLOR_TEXT_HOVER : GuiPainter.COLOR_TEXT);

        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

        int textX = this.getX() + (this.width - ctx.textRenderer().getWidth(this.getMessage())) / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        context.drawTextWithShadow(ctx.textRenderer(), this.getMessage(), textX, textY, textColor);
    }
}