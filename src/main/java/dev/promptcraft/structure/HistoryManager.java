package dev.promptcraft.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

public class HistoryManager {
    private static final Map<UUID, Deque<List<BlockSnapshot>>> UNDO_STACKS = new HashMap<>();
    private static final Map<UUID, Deque<List<BlockSnapshot>>> REDO_STACKS = new HashMap<>();

    public static void pushUndo(ServerPlayerEntity player, List<BlockSnapshot> snapshots) {
        UNDO_STACKS.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>()).push(snapshots);
        REDO_STACKS.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>()).clear();
    }

    public static boolean undo(ServerPlayerEntity player) {
        return applyStack(player, UNDO_STACKS, REDO_STACKS);
    }

    public static boolean redo(ServerPlayerEntity player) {
        return applyStack(player, REDO_STACKS, UNDO_STACKS);
    }

    private static boolean applyStack(ServerPlayerEntity player, Map<UUID, Deque<List<BlockSnapshot>>> fromStackMap, Map<UUID, Deque<List<BlockSnapshot>>> toStackMap) {
        Deque<List<BlockSnapshot>> fromStack = fromStackMap.get(player.getUuid());
        if (fromStack == null || fromStack.isEmpty()) return false;

        List<BlockSnapshot> snapshots = fromStack.pop();
        ServerWorld world = player.getEntityWorld();
        List<BlockSnapshot> oppositeSnapshots = new ArrayList<>();

        for (BlockSnapshot snap : snapshots) {
            BlockState currentState = world.getBlockState(snap.pos());
            BlockEntity currentBe = world.getBlockEntity(snap.pos());
            oppositeSnapshots.add(new BlockSnapshot(snap.pos(), currentState, currentBe != null ? currentBe.createNbt(world.getRegistryManager()) : null));
        }

        toStackMap.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>()).push(oppositeSnapshots);

        // Восстановление идет плавно и со звуком через RestoreTask
        dev.promptcraft.task.TaskManager.addTask(new dev.promptcraft.task.RestoreTask(player, snapshots, null));

        return true;
    }
}