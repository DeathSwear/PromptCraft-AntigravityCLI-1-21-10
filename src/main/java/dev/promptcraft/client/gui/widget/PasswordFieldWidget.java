package dev.promptcraft.client.gui.widget;

import dev.promptcraft.client.gui.SettingsContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class PasswordFieldWidget extends TextFieldWidget {

    private final SettingsContext ctx;

    // Своё зеркало выделения: super хранит его в приватных полях, а нам нужно рисовать.
    private int selStart = 0;
    private int selEnd = 0;

    public PasswordFieldWidget(SettingsContext ctx, TextRenderer textRenderer,
                               int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        this.ctx = ctx;
    }

    @Override
    public void setSelectionStart(int cursor) {
        super.setSelectionStart(cursor);
        this.selStart = MathHelper.clamp(cursor, 0, this.getText().length());
    }

    @Override
    public void setSelectionEnd(int index) {
        super.setSelectionEnd(index);
        this.selEnd = MathHelper.clamp(index, 0, this.getText().length());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;

        int bgColor = this.isFocused() ? 0xFF3A3A3A : 0xFF2D2D2D;
        context.fill(
                this.getX() - 7,
                this.getY() - 6,
                this.getX() + this.width + 7,
                this.getY() + this.height + 6,
                bgColor
        );

        String real = this.getText();
        String shown = real.isEmpty() ? "" : "*".repeat(Math.min(real.length(), 32));
        int shownLen = shown.length();

        TextRenderer tr = ctx.textRenderer();

        // Подсветка выделения цветом темы. Рисуем ДО текста, чтобы звёздочки читались поверх.
        int a = Math.min(Math.min(this.selStart, this.selEnd), shownLen);
        int b = Math.min(Math.max(this.selStart, this.selEnd), shownLen);
        if (a != b) {
            int x1 = this.getX() + tr.getWidth(shown.substring(0, a));
            int x2 = this.getX() + tr.getWidth(shown.substring(0, b));
            int themeRgb = ctx.themeColorArgb() & 0x00FFFFFF;
            int selColor = 0x99000000 | themeRgb; // ~60% альфа, цвет темы из раздела Theme
            context.fill(x1, this.getY() - 1, x2, this.getY() + 11, selColor);
        }

        context.drawTextWithShadow(
                tr,
                shown,
                this.getX(),
                this.getY() + 2,
                this.active ? 0xE0E0E0 : 0x707070
        );

        // Курсор мигает только когда нет активного выделения, и стоит на реальной позиции.
        if (this.isFocused() && this.getSelectedText().isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cur = Math.min(this.selStart, shownLen);
            int cursorX = this.getX() + tr.getWidth(shown.substring(0, cur));
            context.fill(cursorX + 1, this.getY(), cursorX + 2, this.getY() + 11, 0xFFFFFFFF);
        }
    }
}