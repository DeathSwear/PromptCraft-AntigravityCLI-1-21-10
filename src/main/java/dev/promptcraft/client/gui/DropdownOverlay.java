package dev.promptcraft.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Универсальный модальный список «выбери одно значение».
 * Используется для провайдера, языка, режима выбора области и режима генерации.
 */
public final class DropdownOverlay implements Overlay {

    /** Один пункт: код, подпись, необязательная иконка. */
    public record Item(String code, String label, Identifier icon) {
        public Item(String code, String label) {
            this(code, label, null);
        }
    }

    private static final int ITEM_H = 20;
    private static final int ITEM_STEP = 22;
    private static final int FIRST_ITEM_OFFSET = 30;

    private final SettingsContext ctx;
    private final String title;
    private final int overlayW;
    private final List<Item> items;
    private final Supplier<String> selectedCode;
    private final Consumer<String> onSelect;

    public DropdownOverlay(SettingsContext ctx,
                           String title,
                           int overlayW,
                           List<Item> items,
                           Supplier<String> selectedCode,
                           Consumer<String> onSelect) {
        this.ctx = ctx;
        this.title = title;
        this.overlayW = overlayW;
        this.items = items;
        this.selectedCode = selectedCode;
        this.onSelect = onSelect;
    }

    /** Готовый дропдаун провайдеров — вся таблица берётся из ProviderRegistry. */
    public static DropdownOverlay providers(SettingsContext ctx, Consumer<String> onSelect) {
        List<Item> items = ProviderRegistry.ALL.stream()
                .map(p -> new Item(p.code(), p.displayName(), p.icon()))
                .toList();
        return new DropdownOverlay(ctx, ctx.t("Select Provider", "Выберите провайдера"), 220,
                items, () -> ctx.state().provider(), onSelect);
    }

    /** Высота окна считается от числа пунктов — раньше была захардкожена. */
    private int overlayH() {
        return FIRST_ITEM_OFFSET + items.size() * ITEM_STEP + 8;
    }

    private int originX() {
        return ctx.screenWidth() / 2 - overlayW / 2;
    }

    private int originY() {
        return ctx.screenHeight() / 2 - overlayH() / 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.getMatrices().pushMatrix();

        int ox = originX();
        int oy = originY();
        int overlayH = overlayH();

        GuiPainter.overlayBase(context, ctx.screenWidth(), ctx.screenHeight(), ox, oy, overlayW, overlayH);
        context.drawTextWithShadow(ctx.textRenderer(), title, ox + 10, oy + 6, 0xFFFFFF);
        GuiPainter.closeButton(context, ctx.textRenderer(), ox + overlayW - 22, oy + 5);

        int themeColorInt = ctx.themeColorArgb();
        String selected = selectedCode.get();

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int itemY = oy + FIRST_ITEM_OFFSET + i * ITEM_STEP;
            boolean isSelected = item.code().equals(selected);
            boolean hasIcon = item.icon() != null;

            GuiPainter.listItem(context, ctx.textRenderer(), item.label(),
                    ox + 10, itemY, overlayW - 20, ITEM_H,
                    isSelected, themeColorInt, hasIcon ? 30 : 6);

            if (hasIcon) {
                GuiPainter.iconSlot(context, item.icon(), ox + 14, itemY + 1);
            }
        }

        context.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int ox = originX();
        int oy = originY();
        int overlayH = overlayH();

        if (GuiPainter.closeButtonHit(mouseX, mouseY, ox + overlayW - 22, oy + 5)) {
            ctx.closeOverlay();
            return true;
        }

        for (int i = 0; i < items.size(); i++) {
            int itemY = oy + FIRST_ITEM_OFFSET + i * ITEM_STEP;
            if (GuiPainter.hit(mouseX, mouseY, ox + 10, itemY, overlayW - 20, ITEM_H)) {
                ctx.closeOverlay();
                onSelect.accept(items.get(i).code());
                return true;
            }
        }

        if (!GuiPainter.hit(mouseX, mouseY, ox, oy, overlayW, overlayH)) {
            ctx.closeOverlay();
        }

        return true;
    }
}