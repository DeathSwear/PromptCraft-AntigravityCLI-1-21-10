package dev.promptcraft.client.gui.tab;

import dev.promptcraft.PromptCraftMod;
import dev.promptcraft.client.gui.DropdownOverlay;
import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.ModelListClient;
import dev.promptcraft.client.gui.ModelPickerOverlay;
import dev.promptcraft.client.gui.ProviderRegistry;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.widget.IconButton;
import dev.promptcraft.client.gui.widget.ModelSelectButton;
import dev.promptcraft.client.gui.widget.PasswordFieldWidget;
import dev.promptcraft.client.gui.widget.ProviderSelectButton;
import dev.promptcraft.client.gui.widget.FlatButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class ApiTab extends AbstractSettingsTab {

    private static final Identifier REFRESH_ICON =
            new Identifier(PromptCraftMod.MOD_ID, "textures/gui/refresh_icon.png");

    private static final String[] BUILD_MODE_CODES = {"creative", "precise"};

    private ProviderSelectButton providerButton;
    private PasswordFieldWidget apiKeyField;
    private ModelSelectButton modelButton;
    private IconButton refreshButton;
    private FlatButton buildModeButton;

    private final List<String> cachedModels = new ArrayList<>();
    private boolean isFetching = false;
    private String fetchError = null;

    public ApiTab(SettingsContext ctx, Layout layout) {
        super(ctx, layout);
    }

    @Override
    public String title() {
        return "API";
    }

    @Override
    public void init() {
        int x = layout.contentX() - 5;
        int y = layout.contentY();

        providerButton = add(new ProviderSelectButton(ctx, x, y, 190, 22,
                Text.literal(ProviderRegistry.displayName(state().provider())),
                b -> ctx.openOverlay(DropdownOverlay.providers(ctx, this::applyProvider))));

        apiKeyField = add(new PasswordFieldWidget(ctx, ctx.textRenderer(),
                layout.contentX() + 2, y + 46, 178, 12, Text.literal("API Key")));
        apiKeyField.setMaxLength(300);
        apiKeyField.setText(state().apiKeyFor(state().provider()));
        apiKeyField.setDrawsBackground(false);
        apiKeyField.setChangedListener(text -> state().putApiKey(state().provider(), text));

        modelButton = add(new ModelSelectButton(ctx, x, y + 84, 160, 22,
                Text.literal(shortenModelName(state().model())),
                b -> openModelPicker()));

        refreshButton = add(new IconButton(layout.contentX() + 160, y + 84, 22, 22,
                REFRESH_ICON, b -> fetchModels()));

        buildModeButton = add(new FlatButton(ctx, x, y + 126, 190, 20,
                Text.literal(buildModeLabel(state().buildMode())),
                b -> openBuildModeDropdown()));
    }

    // --- провайдер ---

    private void applyProvider(String code) {
        state().setProvider(code);
        providerButton.setMessage(Text.literal(ProviderRegistry.displayName(code)));

        // Список моделей у нового провайдера свой — старый кэш недействителен.
        cachedModels.clear();
        fetchError = null;

        state().setModel(ProviderRegistry.defaultModel(code));
        modelButton.setMessage(Text.literal(shortenModelName(state().model())));
        apiKeyField.setText(state().apiKeyFor(code));
    }

    // --- модели ---

    private void openModelPicker() {
        fetchError = null;

        if (cachedModels.isEmpty()) {
            fetchModels();
            return;
        }

        ctx.openOverlay(new ModelPickerOverlay(ctx, cachedModels, this::applyModel));
    }

    private void applyModel(String model) {
        state().setModel(model);
        modelButton.setMessage(Text.literal(shortenModelName(model)));
    }

    private void fetchModels() {
        if (isFetching) return;

        String key = apiKeyField.getText().trim();
        if (key.isEmpty()) {
            fetchError = t("API key is empty!", "API ключ пуст!");
            return;
        }

        isFetching = true;
        fetchError = null;

        ModelListClient.fetch(
                state().provider(),
                key,
                models -> {
                    isFetching = false;
                    cachedModels.clear();
                    cachedModels.addAll(models);
                    ctx.openOverlay(new ModelPickerOverlay(ctx, cachedModels, this::applyModel));
                },
                error -> {
                    isFetching = false;
                    fetchError = "unsupported".equals(error)
                            ? t("Model list is not supported for this provider yet.",
                                "Список моделей пока не поддерживается для этого провайдера.")
                            : error;
                }
        );
    }

    private String shortenModelName(String value) {
        if (value == null || value.isBlank()) {
            return t("Select model", "Выбрать модель");
        }

        int maxWidth = 130;
        if (ctx.textRenderer().getWidth(value) > maxWidth) {
            int availableWidth = maxWidth - ctx.textRenderer().getWidth("...");
            return ctx.textRenderer().trimToWidth(value, availableWidth) + "...";
        }

        return value;
    }

    // --- режим генерации ---

    private void openBuildModeDropdown() {
        List<DropdownOverlay.Item> items = List.of(
                new DropdownOverlay.Item(BUILD_MODE_CODES[0], t("High Creativity", "Высокая креативность")),
                new DropdownOverlay.Item(BUILD_MODE_CODES[1], t("Strict Prompt", "Строго по промпту"))
        );

        ctx.openOverlay(new DropdownOverlay(
                ctx,
                t("Select Generation Mode", "Выберите режим генерации"),
                220,
                items,
                () -> state().buildMode(),
                code -> {
                    state().setBuildMode(code);
                    buildModeButton.setMessage(Text.literal(buildModeLabel(code)));
                }
        ));
    }

    private String buildModeLabel(String code) {
        if ("precise".equals(code)) return t("Strict Prompt", "Строго по промпту");
        return t("High Creativity", "Высокая креативность");
    }

    // --- отрисовка ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = layout.contentX() - 5;
        int y = layout.contentY();

        context.drawTextWithShadow(ctx.textRenderer(), t("Provider:", "Провайдер:"), x, y - 12, 0xFFFFFF);
        context.drawTextWithShadow(ctx.textRenderer(), t("API Key:", "API-ключ:"), x, y + 30, 0xFFFFFF);
        context.drawTextWithShadow(ctx.textRenderer(), t("Model:", "Модель:"), x, y + 72, 0xFFFFFF);
        context.drawTextWithShadow(ctx.textRenderer(), t("Generation Mode:", "Режим генерации:"), x, y + 114, 0xFFFFFF);

        if (isFetching) {
            context.drawTextWithShadow(ctx.textRenderer(), t("Fetching...", "Загрузка..."), x, y + 150, 0xAAAAAA);
        } else if (fetchError != null) {
            context.drawTextWithShadow(ctx.textRenderer(), fetchError, x, y + 150, 0xFF5555);
        }
    }

    @Override
    public void refreshLabels() {
        if (buildModeButton == null) return;
        buildModeButton.setMessage(Text.literal(buildModeLabel(state().buildMode())));
        modelButton.setMessage(Text.literal(shortenModelName(state().model())));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (apiKeyField == null || !apiKeyField.isFocused()) return false;

        // Ctrl+A: super не умеет выделять всё в кастомном password-поле.
        if (net.minecraft.client.gui.screen.Screen.hasControlDown()
                && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
            apiKeyField.setSelectionStart(0);
            apiKeyField.setSelectionEnd(apiKeyField.getText().length());
            return true;
        }

        return apiKeyField.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (apiKeyField != null && apiKeyField.isFocused()) {
            return apiKeyField.charTyped(chr, modifiers);
        }
        return false;
    }

    /** Экрану нужен обрезанный ключ на сохранение. */
    public String trimmedApiKey() {
        return apiKeyField == null ? "" : apiKeyField.getText().trim();
    }
}