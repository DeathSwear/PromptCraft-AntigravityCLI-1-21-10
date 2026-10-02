package dev.promptcraft.task;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalConnectingBlock; // база панелей и заборов
import net.minecraft.block.PaneBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Пересчёт состояния "соединяющихся" блоков (стеклянные панели, решётки, заборы,
 * стены, лестницы, редстоун) по их соседям.
 *
 * Зачем: при массовой установке через setBlockState блок кладётся в дефолтном
 * состоянии и соседи не уведомляются, поэтому панели/заборы не соединяются друг
 * с другом и со стенами (рендерятся как одинокий столбик). Прогоняем их вторым
 * проходом ПОСЛЕ того, как все блоки уже стоят - тогда все соседи на месте и
 * форма считается правильно.
 */
public final class ConnectingBlocks {
    private ConnectingBlocks() {}

    public static boolean needsConnectionUpdate(BlockState state) {
        Block b = state.getBlock();
        return b instanceof HorizontalConnectingBlock // PaneBlock (стекло/решётки) + FenceBlock
            || b instanceof PaneBlock
            || b instanceof FenceBlock
            || b instanceof WallBlock
            || b instanceof StairsBlock
            || b instanceof RedstoneWireBlock;
    }

    /** Пересчитывает состояние блока в pos, опрашивая все 6 соседей. */
    public static void refresh(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!needsConnectionUpdate(state)) return;

        BlockState updated = state;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            updated = updated.getStateForNeighborUpdate(world, world, pos, dir, neighborPos, neighborState, world.getRandom());
        }

        if (updated != state) {
            world.setBlockState(pos, updated, Block.NOTIFY_ALL);
        }
    }
}