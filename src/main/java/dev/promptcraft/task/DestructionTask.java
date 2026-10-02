package dev.promptcraft.task;

import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftLang;
import dev.promptcraft.session.GenerationSession;
import dev.promptcraft.structure.BlockSnapshot;
import dev.promptcraft.structure.HistoryManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class DestructionTask implements Task {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos min, max;
    private final GenerationSession session;
    private final Runnable onComplete;
    private final List<BlockSnapshot> snapshotList = new ArrayList<>();

    private int currentX, currentY, currentZ;

    public DestructionTask(ServerPlayerEntity player, BlockPos min, BlockPos max, GenerationSession session, Runnable onComplete) {
        this.player = player;
        this.world = player.getEntityWorld();
        this.min = min;
        this.max = max;
        this.session = session;
        this.onComplete = onComplete;
        this.currentX = min.getX();
        this.currentY = min.getY();
        this.currentZ = min.getZ();
        if (session != null) session.markDestructionStarted();
    }

    @Override
    public boolean tick() {
        if (session != null && session.isCancelled()) {
            rollbackImmediately();
            return true;
        }

        int blocksPerTick = 1000;
        int processed = 0;
        boolean animation = PromptCraftConfigManager.get().enableDestructionAnimation;

        while (processed < blocksPerTick) {
            if (currentY > max.getY()) {
                HistoryManager.pushUndo(player, snapshotList);
                if (session != null) session.markDestructionComplete();
                player.sendMessage(Text.literal(PromptCraftLang.t("Area cleared. Generating...", "Область очищена. Генерация...")).formatted(Formatting.GREEN), false);
                if (onComplete != null) onComplete.run();
                return true;
            }

            BlockPos pos = new BlockPos(currentX, currentY, currentZ);
            BlockState state = world.getBlockState(pos);
            BlockEntity be = world.getBlockEntity(pos);

            snapshotList.add(new BlockSnapshot(pos, state, be != null ? be.createNbt(world.getRegistryManager()) : null));

            if (!state.isAir()) {
                if (animation) {
                    world.breakBlock(pos, false);
                } else {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                }
            }
            processed++;

            currentX++;
            if (currentX > max.getX()) {
                currentX = min.getX();
                currentZ++;
                if (currentZ > max.getZ()) {
                    currentZ = min.getZ();
                    currentY++;
                }
            }
        }
        return false;
    }

    private void rollbackImmediately() {
        for (BlockSnapshot snap : snapshotList) {
            world.setBlockState(snap.pos(), snap.state(), BlockPlacementUtil.flagsFor(snap.state()));
            if (snap.nbt() != null) {
                BlockEntity be = world.getBlockEntity(snap.pos());
                if (be != null) be.read(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY, world.getRegistryManager(), snap.nbt()));
            }
        }
        if (session != null) session.markDestructionComplete();
        player.sendMessage(Text.literal(PromptCraftLang.t("Generation cancelled. Area restored.", "Генерация отменена. Область восстановлена.")).formatted(Formatting.YELLOW), false);
    }
}
