package dev.promptcraft;

import dev.promptcraft.item.SelectionBrushItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PromptCraftItems {
    public static final Item SELECTION_BRUSH = new SelectionBrushItem(
            new Item.Settings().maxCount(1)
    );

    private PromptCraftItems() {
    }

    public static void register() {
        Registry.register(
                Registries.ITEM,
                Identifier.of(PromptCraftMod.MOD_ID, "selection_brush"),
                SELECTION_BRUSH
        );
    }
}
