package dev.promptcraft.client.gui.tab;

import dev.promptcraft.PromptCraftMod;
import dev.promptcraft.client.gui.GuiColors;
import dev.promptcraft.client.gui.GuiPainter;
import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.widget.IconButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ThemeTab extends AbstractSettingsTab {

    private static final Identifier RESET_COLOR_ICON =
            new Identifier(PromptCraftMod.MOD_ID, "textures/gui/reload_icon.png");

    private static final int SV_SIZE = 80;
    private static final int SV_CELLS = 20;
    private static final int CELL_SIZE = 4;
    private static final int HUE_W = 10;
    private static final int HUE_H = 80;
    private static final int HUE_SEGMENTS = 24;
    private static final int PREVIEW_SIZE = 20;

    private TextFieldWidget hexColorField;
    private IconButton resetColorButton;

    private boolean draggingSV = false;
    private boolean draggingHue = false;

    public ThemeTab(SettingsContext ctx, Layout layout) {
        super(ctx, layout);
    }

    @Override
    public String title() {
        return t("Theme", "Тема");
    }

    private int svX() {
        return layout.contentX();
    }

    private int svY() {
        return layout.contentY() + 5;
    }

    private int hueX() {
        return svX() + SV_SIZE + 8;
    }

    private int previewX() {
        return hueX() + HUE_W + 12;
    }

    @Override
    public void init() {
        hexColorField = add(new TextFieldWidget(ctx.textRenderer(),
                layout.contentX() + 10, layout.contentY() + 95, 60, 16, Text.literal("Hex")));
        hexColorField.setMaxLength(7);
        hexColorField.setText(state().themeColor());
        hexColorField.setDrawsBackground(false);
        hexColorField.setChangedListener(text -> {
            if (!text.startsWith("#") || text.length() != 7) return;
            try {
                Integer.parseInt(text.substring(1), 16);
                state().setThemeColor(text);
            } catch (Exception ignored) {
                // частичный ввод — молча игнорируем, как в оригинале
            }
        });

        resetColorButton = add(new IconButton(previewX(), svY() + PREVIEW_SIZE + 8, 20, 20,
                RESET_COLOR_ICON, b -> {
            state().resetThemeColor();
            hexColorField.setText(state().themeColor());
        }));
        resetColorButton.setTooltip(Tooltip.of(Text.literal(t("Reset to default", "Сбросить по умолчанию"))));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderColorPicker(context);

        // Подложка под hex-полем.
        context.fill(layout.contentX(), layout.contentY() + 92,
                layout.contentX() + SV_SIZE, layout.contentY() + 112, GuiPainter.COLOR_SLOT);

        // Текст в поле центрируем каждый кадр — длина меняется при вводе.
        int textW = ctx.textRenderer().getWidth(hexColorField.getText());
        hexColorField.setX(layout.contentX() + (SV_SIZE - textW) / 2);
    }

    private void renderColorPicker(DrawContext context) {
        int svX = svX();
        int svY = svY();
        int hueX = hueX();

        float hue = state().pickerHue();

        for (int row = 0; row < SV_CELLS; row++) {
            for (int col = 0; col < SV_CELLS; col++) {
                float s = col / (float) SV_CELLS;
                float v = 1f - row / (float) SV_CELLS;
                int color = GuiColors.hsvToRgb(hue, s, v);
                context.fill(svX + col * CELL_SIZE, svY + row * CELL_SIZE,
                        svX + col * CELL_SIZE + CELL_SIZE, svY + row * CELL_SIZE + CELL_SIZE,
                        0xFF000000 | color);
            }
        }

        int segH = HUE_H / HUE_SEGMENTS;
        for (int i = 0; i < HUE_SEGMENTS; i++) {
            int c = GuiColors.hsvToRgb(i / (float) HUE_SEGMENTS, 1f, 1f);
            context.fill(hueX, svY + i * segH, hueX + HUE_W, svY + i * segH + segH, 0xFF000000 | c);
        }

        int previewX = previewX();
        context.fill(previewX, svY, previewX + PREVIEW_SIZE, svY + PREVIEW_SIZE, ctx.themeColorArgb());

        int dotX = svX + (int) (state().pickerSat() * SV_SIZE);
        int dotY = svY + (int) ((1f - state().pickerVal()) * SV_SIZE);
        context.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, 0xFFFFFFFF);
        context.fill(dotX, dotY, dotX + 1, dotY + 1, 0xFF000000);

        int hY = svY + (int) (hue * HUE_H);
        context.fill(hueX - 2, hY - 1, hueX + HUE_W + 2, hY + 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (GuiPainter.hit(mouseX, mouseY, svX(), svY(), SV_SIZE, SV_SIZE)) {
            draggingSV = true;
            updateSV(mouseX, mouseY);
            return true;
        }

        if (GuiPainter.hit(mouseX, mouseY, hueX(), svY(), HUE_W, HUE_H)) {
            draggingHue = true;
            updateHue(mouseY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSV) {
            updateSV(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            updateHue(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased() {
        draggingSV = false;
        draggingHue = false;
    }

    private void updateSV(double mouseX, double mouseY) {
        float sat = (float) (mouseX - svX()) / SV_SIZE;
        float val = 1f - (float) (mouseY - svY()) / SV_SIZE;
        state().setSaturationValue(sat, val);
        hexColorField.setText(state().themeColor());
    }

    private void updateHue(double mouseY) {
        state().setHue((float) (mouseY - svY()) / HUE_H);
        hexColorField.setText(state().themeColor());
    }

    @Override
    public void refreshLabels() {
        if (resetColorButton != null) {
            resetColorButton.setTooltip(Tooltip.of(Text.literal(t("Reset to default", "Сбросить по умолчанию"))));
        }
    }
}