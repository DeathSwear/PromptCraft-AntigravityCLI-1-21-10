package dev.promptcraft.client.gui.tab;

import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.SettingsState;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractSettingsTab implements SettingsTab {

    protected final SettingsContext ctx;
    protected final Layout layout;

    private final List<ClickableWidget> owned = new ArrayList<>();

    protected AbstractSettingsTab(SettingsContext ctx, Layout layout) {
        this.ctx = ctx;
        this.layout = layout;
    }

    /**
     * Регистрирует виджет и в экране, и в этом табе.
     * Благодаря этому ручной список visible/active больше не нужен —
     * забыть добавить виджет в setVisible физически невозможно.
     */
    protected <T extends ClickableWidget> T add(T widget) {
        owned.add(widget);
        return ctx.addWidget(widget);
    }

    @Override
    public void setVisible(boolean visible) {
        for (ClickableWidget widget : owned) {
            widget.visible = visible;
            widget.active = visible;
        }
        onVisibilityChanged(visible);
    }

    /** Хук для табов, где active зависит не только от выбранного раздела. */
    protected void onVisibilityChanged(boolean visible) {
    }

    protected SettingsState state() {
        return ctx.state();
    }

    protected String t(String en, String ru) {
        return ctx.t(en, ru);
    }
}