package dev.promptcraft.client.gui.widget;

import dev.promptcraft.client.gui.GuiPainter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class IconButton extends ButtonWidget {

    private final Identifier texture;
    private final int iconSize;

    public IconButton(int x, int y, int width, int height, Identifier texture, PressAction onPress) {
        this(x, y, width, height, texture, 32, onPress);
    }

    public IconButton(int x, int y, int width, int height, Identifier texture, int iconSize, PressAction onPress) {
        super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.texture = texture;
        this.iconSize = iconSize;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        int bgColor = this.isHovered() ? GuiPainter.COLOR_SLOT_HOVER : GuiPainter.COLOR_SLOT;
        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

        int displaySize = 16;
        int offsetX = (this.width - displaySize) / 2;
        int offsetY = (this.height - displaySize) / 2;

        if (iconSize == 16) {
            context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, this.getX() + offsetX, this.getY() + offsetY, 0, 0, 16, 16, 16, 16);
        } else {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) (this.getX() + offsetX), (float) (this.getY() + offsetY));
            context.getMatrices().scale(0.5f, 0.5f);
            context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, 32, 32, 32, 32);
            context.getMatrices().popMatrix();
        }
    }
}