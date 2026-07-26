package dev.promptcraft.client.gui.tab;

import net.minecraft.client.gui.DrawContext;

/** Один раздел экрана настроек. Владеет своими виджетами и своей отрисовкой. */
public interface SettingsTab {

    /** Подпись в боковом меню (локализованная, вычисляется каждый кадр). */
    String title();

    /** Создать и зарегистрировать виджеты. Вызывается один раз из Screen#init. */
    void init();

    /** Отрисовка подписей и кастомной графики. Виджеты рисует сам Screen. */
    default void render(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    /** Показать/скрыть все виджеты этого таба. */
    void setVisible(boolean visible);

    /** Пересобрать подписи — например после смены языка. */
    default void refreshLabels() {
    }

    default void tick() {
    }

    /** true — клик обработан целиком и до виджетов доходить не нужно. */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    default void mouseReleased() {
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char chr, int modifiers) {
        return false;
    }

    /** Экран закрывается — сохранить черновики. */
    default void onScreenClosed() {
    }
}