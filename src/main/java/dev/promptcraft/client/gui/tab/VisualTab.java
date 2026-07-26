package dev.promptcraft.client.gui.tab;

import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.widget.FlatButton;
import dev.promptcraft.client.gui.widget.OpacitySlider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class VisualTab extends AbstractSettingsTab {

    private FlatButton outlineButton;
    private FlatButton outlineThroughBlocksButton;
    private OpacitySlider opacitySlider;

    public VisualTab(SettingsContext ctx, Layout layout) {
        super(ctx, layout);
    }

    @Override
    public String title() {
        return t("Visual", "Визуал");
    }

    @Override
    public void init() {
        int x = layout.contentX() - 5;
        int y = layout.contentY();

        outlineButton = add(new FlatButton(ctx, x, y + 5, 190, 20,
                Text.literal(outlineLabel()),
                b -> {
                    state().toggleThickOutline();
                    b.setMessage(Text.literal(outlineLabel()));
                }));

        outlineThroughBlocksButton = add(new FlatButton(ctx, x, y + 45, 190, 20,
                Text.literal(throughBlocksLabel()),
                b -> {
                    state().toggleOutlineThroughBlocks();
                    b.setMessage(Text.literal(throughBlocksLabel()));
                }));

        opacitySlider = add(new OpacitySlider(ctx, x, y + 85, 190, 20, state().fillOpacity()));
    }

    private String outlineLabel() {
        return t("Outline: ", "Обводка: ")
                + (state().thickOutline() ? t("Thick", "Жирная") : t("Vanilla", "Обычная"));
    }

    private String throughBlocksLabel() {
        return t("Contour through blocks: ", "Контур сквозь блоки: ")
                + (state().outlineThroughBlocks() ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = layout.contentX() - 5;
        int y = layout.contentY();

        context.drawTextWithShadow(ctx.textRenderer(), t("Outline:", "Обводка:"), x, y - 10, 0xFFFFFF);
        context.drawTextWithShadow(ctx.textRenderer(), t("Through blocks:", "Сквозь блоки:"), x, y + 30, 0xFFFFFF);
        context.drawTextWithShadow(ctx.textRenderer(), t("Fill opacity:", "Прозрачность заливки:"), x, y + 70, 0xFFFFFF);
    }

    @Override
    public void refreshLabels() {
        if (outlineButton == null) return;
        outlineButton.setMessage(Text.literal(outlineLabel()));
        outlineThroughBlocksButton.setMessage(Text.literal(throughBlocksLabel()));
        opacitySlider.updateMessage();
    }
}