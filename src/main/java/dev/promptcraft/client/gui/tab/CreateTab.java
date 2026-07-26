package dev.promptcraft.client.gui.tab;

import dev.promptcraft.client.AiStreamState;
import dev.promptcraft.client.PromptDraftState;
import dev.promptcraft.client.gui.DropdownOverlay;
import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.widget.FlatButton;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class CreateTab extends AbstractSettingsTab {

    private static final String[] MODE_CODES = {"manual", "free"};

    private EditBoxWidget promptField;
    private FlatButton generateButton;
    private FlatButton editButton;
    private FlatButton undoButton;
    private FlatButton backButton;
    private FlatButton nextButton;
    private FlatButton modeButton;

    public CreateTab(SettingsContext ctx, Layout layout) {
        super(ctx, layout);
    }

    @Override
    public String title() {
        return t("Create", "Создать");
    }

    @Override
    public void init() {
        int x = layout.contentX() - 5;
        int y = layout.createY();

        promptField = add(new EditBoxWidget(ctx.textRenderer(), x, y, 190, 95,
                Text.literal(t("Prompt", "Запрос")), Text.literal("")));
        promptField.setText(PromptDraftState.get());

        generateButton = add(new FlatButton(ctx, x, y + 100, 90, 20,
                Text.literal(t("Generate", "Создать")),
                b -> ctx.sendAction("generate", promptField.getText(), true)));

        editButton = add(new FlatButton(ctx, x + 100, y + 100, 90, 20,
                Text.literal(t("Edit", "Правка")),
                b -> ctx.sendAction("edit", promptField.getText(), true)));

        undoButton = add(new FlatButton(ctx, x, y + 125, 190, 20,
                Text.literal(undoLabel()),
                b -> ctx.sendAction("undo", "", false)));

        backButton = add(new FlatButton(ctx, x, y + 150, 90, 20,
                Text.literal(t("<< Back", "<< Назад")),
                b -> ctx.sendAction("back", "", false)));

        nextButton = add(new FlatButton(ctx, x + 100, y + 150, 90, 20,
                Text.literal(t("Next >>", "Далее >>")),
                b -> ctx.sendAction("next", "", false)));

        modeButton = add(new FlatButton(ctx, x, y + 175, 190, 20,
                Text.literal(modeLabel(state().generationMode())),
                b -> openModeDropdown()));
    }

    private void openModeDropdown() {
        List<DropdownOverlay.Item> items = List.of(
                new DropdownOverlay.Item(MODE_CODES[0], t("Manual Area", "Ручной выбор")),
                new DropdownOverlay.Item(MODE_CODES[1], t("AI Free Area", "Свободный (от ИИ)"))
        );

        ctx.openOverlay(new DropdownOverlay(
                ctx,
                t("Select Mode", "Выберите режим"),
                200,
                items,
                () -> state().generationMode(),
                code -> {
                    state().setGenerationMode(code);
                    modeButton.setMessage(Text.literal(modeLabel(code)));
                }
        ));
    }

    private String modeLabel(String code) {
        if ("free".equals(code)) return t("Mode: AI Free Area", "Режим: Свободный (от ИИ)");
        return t("Mode: Manual Area", "Режим: Ручной выбор");
    }

    private String undoLabel() {
        return AiStreamState.isGenerating()
                ? t("Cancel Generation", "Отменить генерацию")
                : t("Undo (Clear)", "Отменить (Очистить)");
    }

    @Override
    public void refreshLabels() {
        if (promptField == null) return;
        generateButton.setMessage(Text.literal(t("Generate", "Создать")));
        editButton.setMessage(Text.literal(t("Edit", "Правка")));
        undoButton.setMessage(Text.literal(undoLabel()));
        backButton.setMessage(Text.literal(t("<< Back", "<< Назад")));
        nextButton.setMessage(Text.literal(t("Next >>", "Далее >>")));
        modeButton.setMessage(Text.literal(modeLabel(state().generationMode())));
    }

    @Override
    public void tick() {
        if (promptField == null) return;

        PromptDraftState.set(promptField.getText());

        // Во время генерации ввод и навигация заблокированы, но Undo работает как «отмена».
        boolean generating = AiStreamState.isGenerating();

        generateButton.active = !generating;
        editButton.active = !generating;
        promptField.active = !generating;
        backButton.active = !generating;
        nextButton.active = !generating;
        undoButton.active = true;

        String label = undoLabel();
        if (!label.equals(undoButton.getMessage().getString())) {
            undoButton.setMessage(Text.literal(label));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Клик мимо поля промпта снимает фокус, но клик не съедаем.
        if (promptField != null && promptField.visible && !promptField.isMouseOver(mouseX, mouseY)) {
            promptField.setFocused(false);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (promptField != null && promptField.isFocused()) {
            return promptField.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (promptField != null && promptField.isFocused()) {
            return promptField.charTyped(chr, modifiers);
        }
        return false;
    }

    @Override
    public void onScreenClosed() {
        if (promptField != null) {
            PromptDraftState.set(promptField.getText());
        }
    }
}