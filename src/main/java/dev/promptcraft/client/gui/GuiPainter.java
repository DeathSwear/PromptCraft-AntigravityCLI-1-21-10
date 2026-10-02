package dev.promptcraft.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Повторяющиеся куски отрисовки в стиле PromptCraft. */
public final class GuiPainter {

    public static final int COLOR_PANEL = 0xFF1E1E1E;
    public static final int COLOR_SLOT = 0xFF2D2D2D;
    public static final int COLOR_SLOT_HOVER = 0xFF3D3D3D;
    public static final int COLOR_SLOT_DISABLED = 0xFF232323;
    public static final int COLOR_TEXT = 0xFFD2D2D2;
    public static final int COLOR_TEXT_HOVER = 0xFFFFFFFF;
    public static final int COLOR_TEXT_DISABLED = 0xFF5A5A5A;
    public static final int COLOR_TEXT_MUTED = 0x6E6E6E;

    private GuiPainter() {
    }

    /** Затемнение всего экрана + рамка модального окна. */
    public static void overlayBase(DrawContext context, int screenW, int screenH,
                                   int ox, int oy, int overlayW, int overlayH) {
        context.fill(0, 0, screenW, screenH, 0x80000000);
        context.fill(ox, oy, ox + overlayW, oy + overlayH, COLOR_PANEL);
        context.fill(ox, oy, ox + overlayW, oy + 1, 0xFF555555);
        context.fill(ox, oy + overlayH - 1, ox + overlayW, oy + overlayH, 0xFF111111);
        context.fill(ox, oy, ox + 1, oy + overlayH, 0xFF555555);
        context.fill(ox + overlayW - 1, oy, ox + overlayW, oy + overlayH, 0xFF111111);
    }

    public static void closeButton(DrawContext context, TextRenderer tr, int closeX, int closeY) {
        context.fill(closeX, closeY, closeX + 18, closeY + 14, COLOR_SLOT);
        context.drawText(tr, "X", closeX + 6, closeY + 3, 0xFFAAAAAA, false);
    }

    public static boolean closeButtonHit(double mouseX, double mouseY, int closeX, int closeY) {
        return hit(mouseX, mouseY, closeX, closeY, 18, 14);
    }

    /** Слот 18x18 с «вдавленной» рамкой и иконкой 16x16 внутри. */
    public static void iconSlot(DrawContext context, Identifier icon, int iconX, int iconY) {
        context.fill(iconX, iconY, iconX + 18, iconY + 18, 0xFF181818);
        context.fill(iconX, iconY, iconX + 18, iconY + 1, 0xFF0A0A0A);
        context.fill(iconX, iconY, iconX + 1, iconY + 18, 0xFF0A0A0A);
        context.fill(iconX, iconY + 17, iconX + 18, iconY + 18, 0xFF4A4A4A);
        context.fill(iconX + 17, iconY, iconX + 18, iconY + 18, 0xFF4A4A4A);

        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, icon, iconX + 1, iconY + 1, 0, 0, 16, 16, 16, 16);
    }

    /** Пункт бокового меню табов. */
    public static void menuItem(DrawContext context, TextRenderer tr, String text,
                               int x, int y, boolean selected, int themeColor) {
        if (selected) {
            context.fill(x, y, x + 120, y + 20, themeColor);
            context.drawText(tr, text, x + 5, y + 6, 0x000000, false);
        } else {
            context.drawTextWithShadow(tr, text, x + 5, y + 6, COLOR_TEXT);
        }
    }

    /** Пункт списка в дропдауне. */
    public static void listItem(DrawContext context, TextRenderer tr, String text,
                               int x, int y, int width, int height,
                               boolean selected, int themeColor, int textOffsetX) {
        context.fill(x, y, x + width, y + height, selected ? themeColor : COLOR_SLOT);
        if (selected) {
            context.drawText(tr, text, x + textOffsetX, y + (height - 8) / 2, 0x000000, false);
        } else {
            context.drawTextWithShadow(tr, text, x + textOffsetX, y + (height - 8) / 2, COLOR_TEXT);
        }
    }

    public static boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}