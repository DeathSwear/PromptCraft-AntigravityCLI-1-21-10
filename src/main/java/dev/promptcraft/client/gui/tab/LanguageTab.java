package dev.promptcraft.client.gui.tab;

import dev.promptcraft.client.gui.DropdownOverlay;
import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.widget.FlatButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

public final class LanguageTab extends AbstractSettingsTab {

    private FlatButton langButton;

    public LanguageTab(SettingsContext ctx, Layout layout) {
        super(ctx, layout);
    }

    @Override
    public String title() {
        return t("Language", "Язык");
    }

    @Override
    public void init() {
        langButton = add(new FlatButton(ctx, layout.contentX() - 5, layout.contentY() + 30, 190, 20,
                Text.literal(t("Change language", "Изменить язык")),
                b -> openLanguageDropdown()));
    }

    private void openLanguageDropdown() {
        List<DropdownOverlay.Item> items = List.of(
                new DropdownOverlay.Item("en", "English"),
                new DropdownOverlay.Item("ru", "Русский")
        );

        ctx.openOverlay(new DropdownOverlay(
                ctx,
                t("Select Language", "Выберите язык"),
                200,
                items,
                () -> state().language(),
                code -> {
                    state().setLanguage(code);
                    // Раньше здесь вручную перечислялись виджеты пяти разных табов.
                    ctx.refreshLabels();
                }
        ));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = layout.contentX() - 5;
        int y = layout.contentY();

        context.drawTextWithShadow(ctx.textRenderer(), t("Current language:", "Текущий язык:"), x, y, 0xFFFFFF);
        context.drawTextWithShadow(ctx.textRenderer(),
                state().isRussian() ? "Русский" : "English", x, y + 12, ctx.themeColorArgb());
    }

    @Override
    public void refreshLabels() {
        if (langButton != null) {
            langButton.setMessage(Text.literal(t("Change language", "Изменить язык")));
        }
    }
}