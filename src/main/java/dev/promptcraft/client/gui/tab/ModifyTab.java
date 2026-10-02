package dev.promptcraft.client.gui.tab;

import dev.promptcraft.client.AiStreamState;
import dev.promptcraft.client.ModifyDraftState;
import dev.promptcraft.client.PromptCraftClient;
import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.widget.FlatButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Вкладка «Изменить» для доработки, расширения или модификации уже существующей
 * постройки в выделенной области (In-painting / Selective Refinement).
 */
public final class ModifyTab extends AbstractSettingsTab {

    private EditBoxWidget promptField;
    private FlatButton applyButton;
    private FlatButton undoButton;

    public ModifyTab(SettingsContext ctx, Layout layout) {
        super(ctx, layout);
    }

    @Override
    public String title() {
        return t("Modify", "Изменить");
    }

    @Override
    public void init() {
        int x = layout.contentX() - 5;
        int y = layout.createY();

        promptField = add(EditBoxWidget.builder()
                .x(x).y(y)
                .placeholder(Text.literal(""))
                .build(ctx.textRenderer(), 190, 95, Text.literal(t("Modification request", "Запрос на изменение"))));
        promptField.setText(ModifyDraftState.get());

        applyButton = add(new FlatButton(ctx, x, y + 100, 190, 20,
                Text.literal(t("Apply Changes", "Применить изменения")),
                b -> ctx.sendAction("modify", promptField.getText(), true)));

        undoButton = add(new FlatButton(ctx, x, y + 125, 190, 20,
                Text.literal(undoLabel()),
                b -> ctx.sendAction("undo", "", false)));
    }

    private String undoLabel() {
        return AiStreamState.isGenerating()
                ? t("Cancel Generation", "Отменить генерацию")
                : t("Undo Last Change", "Отменить изменение");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = layout.contentX() - 5;
        int y = layout.createY() + 155;

        boolean hasSelection = PromptCraftClient.hasCompleteSelection();
        if (hasSelection) {
            int w = Math.abs(PromptCraftClient.getFirstPos().getX() - PromptCraftClient.getSecondPos().getX()) + 1;
            int h = Math.abs(PromptCraftClient.getFirstPos().getY() - PromptCraftClient.getSecondPos().getY()) + 1;
            int d = Math.abs(PromptCraftClient.getFirstPos().getZ() - PromptCraftClient.getSecondPos().getZ()) + 1;
            int vol = w * h * d;

            context.drawTextWithShadow(ctx.textRenderer(),
                    Text.literal(t("Area: ", "Зона: ") + w + "x" + h + "x" + d + " (" + vol + " " + t("blocks", "бл.") + ")").formatted(Formatting.GREEN),
                    x, y, 0xFFFFFF);
            context.drawTextWithShadow(ctx.textRenderer(),
                    Text.literal(t("Existing blocks will be kept & updated", "Блоки сохранятся и дополнятся")).formatted(Formatting.GRAY),
                    x, y + 12, 0x888888);
        } else {
            context.drawTextWithShadow(ctx.textRenderer(),
                    Text.literal(t("Select area with brush first!", "Сначала выделите область кистью!")).formatted(Formatting.RED),
                    x, y, 0xFFFFFF);
            context.drawTextWithShadow(ctx.textRenderer(),
                    Text.literal(t("Use Selection Brush (LMB/RMB)", "Кисть выделения: ЛКМ/ПКМ")).formatted(Formatting.GRAY),
                    x, y + 12, 0x888888);
        }
    }

    @Override
    public void refreshLabels() {
        if (promptField == null) return;
        applyButton.setMessage(Text.literal(t("Apply Changes", "Применить изменения")));
        undoButton.setMessage(Text.literal(undoLabel()));
    }

    @Override
    public void tick() {
        if (promptField == null) return;

        ModifyDraftState.set(promptField.getText());

        boolean generating = AiStreamState.isGenerating();
        boolean hasSelection = PromptCraftClient.hasCompleteSelection();

        applyButton.active = !generating && hasSelection && !promptField.getText().isBlank();
        promptField.active = !generating;
        undoButton.active = true;

        String label = undoLabel();
        if (!label.equals(undoButton.getMessage().getString())) {
            undoButton.setMessage(Text.literal(label));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (promptField != null && promptField.visible && !promptField.isMouseOver(mouseX, mouseY)) {
            promptField.setFocused(false);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (promptField != null && promptField.isFocused()) {
            return promptField.keyPressed(new net.minecraft.client.input.KeyInput(keyCode, scanCode, modifiers));
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (promptField != null && promptField.isFocused()) {
            return promptField.charTyped(new net.minecraft.client.input.CharInput(chr, modifiers));
        }
        return false;
    }

    @Override
    public void onScreenClosed() {
        if (promptField != null) {
            ModifyDraftState.set(promptField.getText());
        }
    }
}
