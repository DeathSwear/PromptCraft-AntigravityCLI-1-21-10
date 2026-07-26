package dev.promptcraft.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ClickableWidget;

/**
 * Узкий контракт «что таб или виджет знает об экране».
 * Благодаря нему табы и виджеты лежат в отдельных пакетах и не требуют
 * доступа к protected-полям Screen.
 */
public interface SettingsContext {

    TextRenderer textRenderer();

    MinecraftClient client();

    int screenWidth();

    int screenHeight();

    SettingsState state();

    /** Регистрирует виджет в экране (обёртка над Screen#addDrawableChild). */
    <T extends ClickableWidget> T addWidget(T widget);

    /** Отправить GUI-действие на сервер. keepOpen=false закрывает экран. */
    void sendAction(String action, String prompt, boolean keepOpen);

    void openOverlay(Overlay overlay);

    void closeOverlay();

    /** Пересобрать подписи всех виджетов — например после смены языка. */
    void refreshLabels();

    default String t(String en, String ru) {
        return state().t(en, ru);
    }

    default void markDirty() {
        state().markDirty();
    }

    default int themeColorArgb() {
        return state().themeColorArgb();
    }
}