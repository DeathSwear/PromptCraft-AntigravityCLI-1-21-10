package dev.promptcraft.item;

import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftLang;
import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.selection.PlayerSelection;
import dev.promptcraft.selection.SelectionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SelectionBrushItem extends Item {
    public SelectionBrushItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient()) return ActionResult.SUCCESS;

        PlayerEntity player = context.getPlayer();
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

        if (!hasAccess(serverPlayer)) {
            serverPlayer.sendMessage(Text.literal(PromptCraftLang.t("Access Denied.", "Доступ запрещён.")).formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }

        BlockPos pos = context.getBlockPos();
        PlayerSelection selection = SelectionManager.get(serverPlayer);

        if (!selection.hasFirst()) {
            serverPlayer.sendMessage(Text.literal(PromptCraftLang.t("Select the first position with left click first.", "Сначала выберите первую позицию левым кликом.")).formatted(Formatting.YELLOW), false);
            return ActionResult.SUCCESS;
        }

        selection.setSecond(pos.toImmutable());
        PromptCraftNetworking.syncSelection(serverPlayer, selection);

        serverPlayer.sendMessage(Text.literal(PromptCraftLang.t("Second position set at ", "Вторая позиция установлена: ") + formatPos(pos)).formatted(Formatting.AQUA), false);
        serverPlayer.sendMessage(Text.literal(PromptCraftLang.t("Selection ready! Area: ", "Область готова! Размер: ") + selection.getWidth() + "x" + selection.getHeight() + "x" + selection.getDepth()).formatted(Formatting.GREEN), false);
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        return ActionResult.PASS;
    }

    public static void setFirstPosition(ServerPlayerEntity player, BlockPos pos) {
        PlayerSelection selection = SelectionManager.get(player);
        if (selection.isComplete()) {
            selection.clear();
        }
        selection.setFirst(pos.toImmutable());
        PromptCraftNetworking.syncSelection(player, selection);
        player.sendMessage(Text.literal(PromptCraftLang.t("First position set at ", "Первая позиция установлена: ") + formatPos(pos)).formatted(Formatting.AQUA), false);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static boolean hasAccess(ServerPlayerEntity player) {
        PromptCraftConfig config = PromptCraftConfigManager.get();
        if (config.isAccessEveryone()) return true;
        return player.hasPermissionLevel(2);
    }
}
