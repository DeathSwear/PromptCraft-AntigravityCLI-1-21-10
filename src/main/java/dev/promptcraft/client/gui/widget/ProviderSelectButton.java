package dev.promptcraft.client.gui.widget;

import dev.promptcraft.client.gui.GuiPainter;
import dev.promptcraft.client.gui.ProviderRegistry;
import dev.promptcraft.client.gui.SettingsContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class ProviderSelectButton extends FlatButton {

    public ProviderSelectButton(SettingsContext ctx, int x, int y, int width, int height, Text message, PressAction onPress) {
        super(ctx, x, y, width, height, message, onPress);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        int bgColor = this.isHovered() ? GuiPainter.COLOR_SLOT_HOVER : GuiPainter.COLOR_SLOT;
        int textColor = this.isHovered() ? GuiPainter.COLOR_TEXT_HOVER : GuiPainter.COLOR_TEXT;

        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

        GuiPainter.iconSlot(context, ProviderRegistry.iconFor(ctx.state().provider()), this.getX() + 4, this.getY() + 2);

        int textY = this.getY() + (this.height - 8) / 2;
        context.drawTextWithShadow(ctx.textRenderer(), this.getMessage(), this.getX() + 34, textY, textColor);
        context.drawTextWithShadow(ctx.textRenderer(), "▼", this.getX() + this.width - 16, textY, textColor);
    }
}