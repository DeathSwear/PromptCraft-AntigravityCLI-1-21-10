package dev.promptcraft;

import dev.promptcraft.item.SelectionBrushItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class PromptCraftItems {
    public static final RegistryKey<Item> SELECTION_BRUSH_KEY = RegistryKey.of(
            RegistryKeys.ITEM,
            Identifier.of(PromptCraftMod.MOD_ID, "selection_brush")
    );

    public static final Item SELECTION_BRUSH = new SelectionBrushItem(
            new Item.Settings().registryKey(SELECTION_BRUSH_KEY).maxCount(1)
    );

    private PromptCraftItems() {
    }

    public static void register() {
        Registry.register(
                Registries.ITEM,
                SELECTION_BRUSH_KEY,
                SELECTION_BRUSH
        );
    }
}
