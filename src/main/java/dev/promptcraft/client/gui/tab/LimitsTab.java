package dev.promptcraft.client.gui.tab;

import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.widget.FlatButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

public final class LimitsTab extends AbstractSettingsTab {

    private FlatButton limitEnabledButton;
    private TextFieldWidget maxWidthField;
    private TextFieldWidget maxHeightField;
    private TextFieldWidget maxDepthField;

    public LimitsTab(SettingsContext ctx, Layout layout) {
        super(ctx, layout);
    }

    @Override
    public String title() {
        return t("Limits", "Лимиты");
    }

    @Override
    public void init() {
        int x = layout.contentX() - 5;
        int y = layout.contentY();

        limitEnabledButton = add(new FlatButton(ctx, x, y, 190, 20,
                Text.literal(limitEnabledLabel()),
                b -> {
                    state().toggleSelectionLimit();
                    b.setMessage(Text.literal(limitEnabledLabel()));
                    setFieldsEditable(state().selectionLimitEnabled());
                }));

        maxWidthField = addSizeField(layout.contentX() + 12, y + 44, "W",
                state().maxSelectionWidth(), state()::setMaxSelectionWidth);
        maxHeightField = addSizeField(layout.contentX() + 85, y + 44, "H",
                state().maxSelectionHeight(), state()::setMaxSelectionHeight);
        maxDepthField = addSizeField(layout.contentX() + 158, y + 44, "D",
                state().maxSelectionDepth(), state()::setMaxSelectionDepth);
    }

    /** Три поля W/H/D были тремя копипастами по 12 строк. */
    private TextFieldWidget addSizeField(int x, int y, String label, int initial, IntConsumer apply) {
        TextFieldWidget field = add(new TextFieldWidget(ctx.textRenderer(), x, y, 45, 16, Text.literal(label)));
        field.setMaxLength(5);
        field.setText(String.valueOf(initial));
        field.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{1,5}"));
        field.setChangedListener(text -> {
            try {
                apply.accept(Integer.parseInt(text));
            } catch (Exception ignored) {
                // пустая строка в процессе ввода — не трогаем значение
            }
        });
        return field;
    }

    private String limitEnabledLabel() {
        return t("Area Limit: ", "Лимит области: ")
                + (state().selectionLimitEnabled() ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"));
    }

    private void setFieldsEditable(boolean editable) {
        if (maxWidthField == null) return;
        for (TextFieldWidget field : new TextFieldWidget[]{maxWidthField, maxHeightField, maxDepthField}) {
            field.active = editable;
            field.setEditable(editable);
        }
    }

    /** Поля остаются видимыми, но неактивными, когда лимит выключен. */
    @Override
    protected void onVisibilityChanged(boolean visible) {
        if (visible) {
            setFieldsEditable(state().selectionLimitEnabled());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = layout.contentX() - 5;
        int y = layout.contentY();

        context.drawTextWithShadow(ctx.textRenderer(),
                t("Build Area Limit:", "Лимит области постройки:"), x, y - 14, 0xFFFFFF);

        int labelColor = state().selectionLimitEnabled() ? 0xFFFFFF : 0x6E6E6E;
        context.drawTextWithShadow(ctx.textRenderer(), t("Max Size:", "Макс. размер:"), x, y + 30, labelColor);
        context.drawTextWithShadow(ctx.textRenderer(), "W:", x, y + 47, labelColor);
        context.drawTextWithShadow(ctx.textRenderer(), "H:", layout.contentX() + 68, y + 47, labelColor);
        context.drawTextWithShadow(ctx.textRenderer(), "D:", layout.contentX() + 141, y + 47, labelColor);
    }

    @Override
    public void refreshLabels() {
        if (limitEnabledButton != null) {
            limitEnabledButton.setMessage(Text.literal(limitEnabledLabel()));
        }
    }
}