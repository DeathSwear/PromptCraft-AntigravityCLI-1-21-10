package dev.promptcraft.task;

import dev.promptcraft.structure.BlockSnapshot;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class RestoreTask implements Task {
    private final ServerWorld world;
    private final List<BlockSnapshot> snapshots;
    private final Runnable onComplete;
    private int index = 0;

    public RestoreTask(ServerPlayerEntity player, List<BlockSnapshot> snapshots, Runnable onComplete) {
        this.world = player.getEntityWorld();
        this.snapshots = snapshots;
        this.onComplete = onComplete;
    }

    @Override
    public boolean tick() {
        int blocksPerTick = 150;
        int processed = 0;
        int checked = 0;

        while (processed < blocksPerTick && checked < 5000 && index < snapshots.size()) {
            BlockSnapshot snap = snapshots.get(index++);
            checked++;
            BlockPos pos = snap.pos();
            BlockState state = snap.state();

            if (world.getBlockState(pos).equals(state)) {
                continue;
            }

            world.setBlockState(pos, state, BlockPlacementUtil.flagsFor(state));
            if (snap.nbt() != null) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be != null) be.read(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY, world.getRegistryManager(), snap.nbt()));
            }

            // Воспроизводим звук установки блока (каждый 5-й блок, чтобы не оглушить игрока)
            if (processed % 5 == 0 && !state.isAir()) {
                world.playSound(null, pos, state.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.5f, 0.8f + world.random.nextFloat() * 0.4f);
            }
            processed++;
        }

        if (index >= snapshots.size()) {
            if (onComplete != null) onComplete.run();
            return true;
        }
        return false;
    }
}