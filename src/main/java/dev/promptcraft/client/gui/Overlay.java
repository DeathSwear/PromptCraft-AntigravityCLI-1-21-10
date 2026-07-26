package dev.promptcraft.client.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Модальный слой поверх экрана настроек (дропдаун, список моделей).
 * Заменяет пять булевых флагов *MenuOpen: активный оверлей теперь один объект,
 * и весь ввод роутится в него, а не размазан по keyPressed/charTyped/mouseClicked.
 */
public interface Overlay {

    void render(DrawContext context, int mouseX, int mouseY, float delta);

    /** true — клик обработан. Оверлей сам закрывает себя через ctx.closeOverlay(). */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    /** true — клавиша съедена (по умолчанию оверлей модальный и глотает всё). */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    default boolean charTyped(char chr, int modifiers) {
        return true;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return true;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    default void mouseReleased() {
    }

    /** Вызывается при открытии — удобно для фокуса поля поиска. */
    default void onOpen() {
    }
}