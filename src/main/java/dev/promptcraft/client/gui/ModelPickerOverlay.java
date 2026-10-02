package dev.promptcraft.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Модальный список моделей: поиск, скролл колесом и драг скроллбара. */
public final class ModelPickerOverlay implements Overlay {

    private static final int OVERLAY_W = 260;
    private static final int OVERLAY_H = 160;
    private static final int LIST_H = 120;
    private static final int ITEM_H = 15;

    private final SettingsContext ctx;
    private final Consumer<String> onSelect;
    private final List<String> allModels;
    private final List<String> filtered = new ArrayList<>();
    private final TextFieldWidget searchField;

    private int scroll = 0;
    private boolean draggingScrollBar = false;

    public ModelPickerOverlay(SettingsContext ctx, List<String> models, Consumer<String> onSelect) {
        this.ctx = ctx;
        this.onSelect = onSelect;
        this.allModels = new ArrayList<>(models);
        this.filtered.addAll(models);

        int ox = originX();
        int oy = originY();

        this.searchField = new TextFieldWidget(ctx.textRenderer(), ox + 110, oy + 5, 120, 14, Text.literal("Search"));
        this.searchField.setDrawsBackground(true);
        this.searchField.setMaxLength(50);
        this.searchField.setChangedListener(text -> applyFilter());
    }

    private int originX() {
        return ctx.screenWidth() / 2 - OVERLAY_W / 2;
    }

    private int originY() {
        return ctx.screenHeight() / 2 - OVERLAY_H / 2;
    }

    private int visibleCount() {
        return LIST_H / ITEM_H;
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() - visibleCount());
    }

    @Override
    public void onOpen() {
        searchField.setText("");
        searchField.setFocused(true);
        scroll = 0;
    }

    private void applyFilter() {
        String query = searchField.getText().toLowerCase();
        filtered.clear();
        for (String m : allModels) {
            if (m.toLowerCase().contains(query)) filtered.add(m);
        }
        scroll = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.getMatrices().pushMatrix();

        int ox = originX();
        int oy = originY();

        GuiPainter.overlayBase(context, ctx.screenWidth(), ctx.screenHeight(), ox, oy, OVERLAY_W, OVERLAY_H);
        context.drawTextWithShadow(ctx.textRenderer(), ctx.t("Select Model", "Выберите модель"), ox + 10, oy + 8, 0xFFFFFF);
        GuiPainter.closeButton(context, ctx.textRenderer(), ox + OVERLAY_W - 22, oy + 5);

        searchField.render(context, mouseX, mouseY, delta);

        int listY = oy + 25;
        int themeColorInt = ctx.themeColorArgb();

        for (int i = 0; i < visibleCount(); i++) {
            int index = scroll + i;
            if (index >= filtered.size()) break;

            String modelId = filtered.get(index);
            int itemY = listY + i * ITEM_H;
            boolean hovered = GuiPainter.hit(mouseX, mouseY, ox + 10, itemY, OVERLAY_W - 25, ITEM_H);

            context.fill(ox + 10, itemY, ox + OVERLAY_W - 15, itemY + ITEM_H,
                    hovered ? themeColorInt : GuiPainter.COLOR_SLOT);
            context.drawTextWithShadow(ctx.textRenderer(), modelId, ox + 15, itemY + 4,
                    hovered ? 0xFFFFFF : GuiPainter.COLOR_TEXT);
        }

        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int scrollBarY = listY + (int) ((scroll / (float) maxScroll) * (LIST_H - 20));
            context.fill(ox + OVERLAY_W - 10, listY, ox + OVERLAY_W - 5, listY + LIST_H, 0xFF111111);
            context.fill(ox + OVERLAY_W - 10, scrollBarY, ox + OVERLAY_W - 5, scrollBarY + 20, themeColorInt);
        }

        context.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int ox = originX();
        int oy = originY();

        if (searchField.mouseClicked(new net.minecraft.client.gui.Click(mouseX, mouseY, new net.minecraft.client.input.MouseInput(button, 0)), false)) {
            return true;
        }

        if (GuiPainter.closeButtonHit(mouseX, mouseY, ox + OVERLAY_W - 22, oy + 5)) {
            ctx.closeOverlay();
            return true;
        }

        int listY = oy + 25;

        if (GuiPainter.hit(mouseX, mouseY, ox + OVERLAY_W - 10, listY, 5, LIST_H)) {
            draggingScrollBar = true;
            updateScrollFromMouse(mouseY, listY);
            return true;
        }

        for (int i = 0; i < visibleCount(); i++) {
            int index = scroll + i;
            if (index >= filtered.size()) break;

            int itemY = listY + i * ITEM_H;
            if (GuiPainter.hit(mouseX, mouseY, ox + 10, itemY, OVERLAY_W - 25, ITEM_H)) {
                String picked = filtered.get(index);
                ctx.closeOverlay();
                onSelect.accept(picked);
                return true;
            }
        }

        if (!GuiPainter.hit(mouseX, mouseY, ox, oy, OVERLAY_W, OVERLAY_H)) {
            ctx.closeOverlay();
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        searchField.keyPressed(new net.minecraft.client.input.KeyInput(keyCode, scanCode, modifiers));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        searchField.charTyped(new net.minecraft.client.input.CharInput(chr, modifiers));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (filtered.isEmpty()) return true;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) amount));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!draggingScrollBar) return false;
        updateScrollFromMouse(mouseY, originY() + 25);
        return true;
    }

    @Override
    public void mouseReleased() {
        draggingScrollBar = false;
    }

    private void updateScrollFromMouse(double mouseY, int listY) {
        int maxScroll = maxScroll();
        if (maxScroll == 0) {
            scroll = 0;
            return;
        }

        float trackHeight = LIST_H - 20f;
        float deltaY = (float) (mouseY - listY - 10f);
        float fraction = Math.max(0, Math.min(1, deltaY / trackHeight));

        scroll = Math.round(fraction * maxScroll);
    }
}