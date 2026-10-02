package dev.promptcraft.client.gui;

/** Геометрия экрана настроек. Единственный источник координат для табов. */
public final class Layout {

    private final int screenW;
    private final int screenH;

    public Layout(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
    }

    public int centerX() {
        return screenW / 2;
    }

    public int centerY() {
        return screenH / 2;
    }

    /** Левая колонка с табами. */
    public int menuX() {
        return centerX() - 180;
    }

    public int menuY() {
        return centerY() - 95;
    }

    public int tabItemW() {
        return 120;
    }

    public int tabItemH() {
        return 20;
    }

    public int tabStep() {
        return 23;
    }

    /** Левый край области контента. */
    public int contentX() {
        return centerX() - 30;
    }

    public int contentY() {
        return centerY() - 75;
    }

    /** Табы «Создать» и «Изменить» начинаются выше остальных. */
    public int createY() {
        return centerY() - 100;
    }

    public int panelLeft() {
        return centerX() - 200;
    }

    public int panelTop() {
        return centerY() - 115;
    }

    public int panelRight() {
        return centerX() + 200;
    }

    public int panelBottom() {
        return centerY() + 115;
    }
}