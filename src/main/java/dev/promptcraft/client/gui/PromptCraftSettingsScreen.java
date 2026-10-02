package dev.promptcraft.client.gui;

import dev.promptcraft.client.AiStreamState;
import dev.promptcraft.client.gui.tab.AnimationsTab;
import dev.promptcraft.client.gui.tab.ApiTab;
import dev.promptcraft.client.gui.tab.CreateTab;
import dev.promptcraft.client.gui.tab.LanguageTab;
import dev.promptcraft.client.gui.tab.LimitsTab;
import dev.promptcraft.client.gui.tab.SettingsTab;
import dev.promptcraft.client.gui.tab.ThemeTab;
import dev.promptcraft.client.gui.tab.VisualTab;
import dev.promptcraft.network.PromptCraftNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Каркас экрана настроек: боковое меню табов, роутинг ввода и сохранение.
 * Вся логика конкретных разделов живёт в client/gui/tab, виджеты — в client/gui/widget.
 */
public class PromptCraftSettingsScreen extends Screen implements SettingsContext {

    private final SettingsState state;

    private final List<SettingsTab> tabs = new ArrayList<>();
    private int selectedTab = 0;

    private Layout layout;
    private ApiTab apiTab;
    private Overlay activeOverlay;

    public PromptCraftSettingsScreen(
            String provider,
            Map<String, String> apiKeys,
            String model,
            boolean showPreview,
            String language,
            String themeColor,
            boolean thickOutline,
            float fillOpacity,
            boolean outlineThroughBlocks,
            boolean selectionLimitEnabled,
            int maxSelectionWidth,
            int maxSelectionHeight,
            int maxSelectionDepth
    ) {
        super(Text.literal("PromptCraft Settings"));
        this.state = new SettingsState(
                provider, apiKeys, model, showPreview, language, themeColor,
                thickOutline, fillOpacity, outlineThroughBlocks,
                selectionLimitEnabled, maxSelectionWidth, maxSelectionHeight, maxSelectionDepth
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        this.layout = new Layout(this.width, this.height);
        this.apiTab = new ApiTab(this, layout);

        tabs.clear();
        tabs.add(new CreateTab(this, layout));
        tabs.add(apiTab);
        tabs.add(new AnimationsTab(this, layout));
        tabs.add(new LanguageTab(this, layout));
        tabs.add(new ThemeTab(this, layout));
        tabs.add(new VisualTab(this, layout));
        tabs.add(new LimitsTab(this, layout));

        for (SettingsTab tab : tabs) {
            tab.init();
        }

        applyTabVisibility();
    }

    private SettingsTab activeTab() {
        return tabs.get(selectedTab);
    }

    private void applyTabVisibility() {
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setVisible(i == selectedTab);
        }
    }

    // --- SettingsContext ---

    @Override
    public TextRenderer textRenderer() {
        return this.textRenderer;
    }

    @Override
    public MinecraftClient client() {
        return this.client;
    }

    @Override
    public int screenWidth() {
        return this.width;
    }

    @Override
    public int screenHeight() {
        return this.height;
    }

    @Override
    public SettingsState state() {
        return state;
    }

    @Override
    public <T extends ClickableWidget> T addWidget(T widget) {
        return this.addDrawableChild(widget);
    }

    @Override
    public void sendAction(String action, String prompt, boolean keepOpen) {
        ClientPlayNetworking.send(new dev.promptcraft.network.PromptCraftPayloads.GuiActionPayload(action, prompt));

        if (keepOpen) {
            AiStreamState.reset();
        } else if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void openOverlay(Overlay overlay) {
        this.activeOverlay = overlay;
        overlay.onOpen();
    }

    @Override
    public void closeOverlay() {
        this.activeOverlay = null;
    }

    @Override
    public void refreshLabels() {
        for (SettingsTab tab : tabs) {
            tab.refreshLabels();
        }
    }

    // --- жизненный цикл ---

    @Override
    public void tick() {
        super.tick();

        if (state.shouldFlush()) {
            flushSave();
        }

        activeTab().tick();
    }

    @Override
    public void removed() {
        super.removed();

        for (SettingsTab tab : tabs) {
            tab.onScreenClosed();
        }

        if (state.isDirty()) {
            flushSave();
        }
    }

    private void flushSave() {
        if (this.client == null || apiTab == null) return;

        Map<String, String> keys = state.apiKeysWith(state.provider(), apiTab.trimmedApiKey());

        ClientPlayNetworking.send(new dev.promptcraft.network.PromptCraftPayloads.SaveGuiPayload(
                state.provider(), keys, state.model(), state.showPreview(),
                state.language(), state.themeColor(), state.thickOutline(),
                state.fillOpacity(), state.outlineThroughBlocks(),
                state.selectionLimitEnabled(), state.maxSelectionWidth(),
                state.maxSelectionHeight(), state.maxSelectionDepth()
        ));
        state.clearDirty();
    }

    // --- ввод ---

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeOverlay != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeOverlay();
                return true;
            }
            return activeOverlay.keyPressed(keyCode, scanCode, modifiers);
        }

        if (activeTab().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activeOverlay != null) {
            return activeOverlay.charTyped(chr, modifiers);
        }

        if (activeTab().charTyped(chr, modifiers)) {
            return true;
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeOverlay != null) {
            return activeOverlay.mouseClicked(mouseX, mouseY, button);
        }

        if (handleTabBarClick(mouseX, mouseY)) {
            return true;
        }

        if (activeTab().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Ручной обход детей: скрытые/неактивные виджеты других табов кликать нельзя.
        for (Element child : this.children()) {
            if (child instanceof ClickableWidget widget && (!widget.visible || !widget.active)) {
                continue;
            }
            if (child.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(child);
                if (button == 0) {
                    this.setDragging(true);
                }
                return true;
            }
        }

        return false;
    }

    private boolean handleTabBarClick(double mouseX, double mouseY) {
        if (mouseX < layout.menuX() || mouseX > layout.menuX() + layout.tabItemW()) {
            return false;
        }

        for (int i = 0; i < tabs.size(); i++) {
            int ty = layout.menuY() + i * layout.tabStep();
            if (mouseY >= ty && mouseY <= ty + layout.tabItemH()) {
                selectedTab = i;
                applyTabVisibility();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeOverlay != null) {
            return activeOverlay.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeOverlay != null && activeOverlay.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        if (activeOverlay == null && activeTab().mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeOverlay != null) {
            activeOverlay.mouseReleased();
        }
        for (SettingsTab tab : tabs) {
            tab.mouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // --- отрисовка ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.fill(layout.panelLeft(), layout.panelTop(), layout.panelRight(), layout.panelBottom(),
                GuiPainter.COLOR_PANEL);
        context.drawTextWithShadow(this.textRenderer, state.t("Settings", "Настройки"),
                layout.menuX(), layout.centerY() - 100, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "esc",
                layout.centerX() + 170, layout.centerY() - 100, GuiPainter.COLOR_TEXT_MUTED);

        int themeColorInt = state.themeColorArgb();
        for (int i = 0; i < tabs.size(); i++) {
            GuiPainter.menuItem(context, this.textRenderer, tabs.get(i).title(),
                    layout.menuX(), layout.menuY() + i * layout.tabStep(), selectedTab == i, themeColorInt);
        }

        activeTab().render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        if (activeOverlay != null) {
            activeOverlay.render(context, mouseX, mouseY, delta);
        }
    }
}