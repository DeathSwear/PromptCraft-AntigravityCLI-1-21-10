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
import java.util.function.Consumer;

public class DestructionTask implements Task {
    private static final int MAX_SCANNED_PER_TICK = 50000;

    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos min, max;
    private final GenerationSession session;
    private final Consumer<List<BlockSnapshot>> onComplete;
    private final List<BlockSnapshot> snapshotList = new ArrayList<>();

    private int currentX, currentY, currentZ;
    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();
    private final long totalVolume;
    private long scannedVolume = 0L;

    public DestructionTask(ServerPlayerEntity player, BlockPos min, BlockPos max, GenerationSession session, Runnable onComplete) {
        this(player, min, max, session, onComplete != null ? snaps -> onComplete.run() : null);
    }

    public DestructionTask(ServerPlayerEntity player, BlockPos min, BlockPos max, GenerationSession session, Consumer<List<BlockSnapshot>> onComplete) {
        this.player = player;
        this.world = player.getEntityWorld();
        this.min = new BlockPos(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ())
        );
        this.max = new BlockPos(
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ())
        );
        this.session = session;
        this.onComplete = onComplete;
        this.currentX = this.min.getX();
        this.currentY = this.min.getY();
        this.currentZ = this.min.getZ();

        long dx = (long) (this.max.getX() - this.min.getX() + 1);
        long dy = (long) (this.max.getY() - this.min.getY() + 1);
        long dz = (long) (this.max.getZ() - this.min.getZ() + 1);
        this.totalVolume = Math.max(1L, dx * dy * dz);

        if (session != null) session.markDestructionStarted();
    }

    @Override
    public boolean tick() {
        if (session != null && session.isCancelled()) {
            rollbackImmediately();
            return true;
        }

        boolean animation = PromptCraftConfigManager.get().enableDestructionAnimation;
        int maxBrokenPerTick = animation ? 300 : 2000;
        int broken = 0;
        int scanned = 0;

        while (scanned < MAX_SCANNED_PER_TICK && broken < maxBrokenPerTick) {
            if (currentY > max.getY()) {
                if (session != null) session.markDestructionComplete();
                if (onComplete != null) {
                    onComplete.accept(snapshotList);
                } else {
                    HistoryManager.pushUndo(player, snapshotList);
                    player.sendMessage(Text.literal(PromptCraftLang.t("Area cleared.", "Область очищена.")).formatted(Formatting.GREEN), false);
                }
                return true;
            }

            mutablePos.set(currentX, currentY, currentZ);
            BlockState state = world.getBlockState(mutablePos);
            scanned++;
            scannedVolume++;

            if (!state.isAir()) {
                BlockPos pos = mutablePos.toImmutable();
                BlockEntity be = world.getBlockEntity(pos);
                snapshotList.add(new BlockSnapshot(pos, state, be != null ? be.createNbt(world.getRegistryManager()) : null));

                if (animation) {
                    world.breakBlock(pos, false);
                } else {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                }
                broken++;
            }

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

        if (totalVolume > 50000 && scannedVolume < totalVolume) {
            int pct = (int) Math.min(99L, (scannedVolume * 100L) / totalVolume);
            player.sendMessage(Text.literal(PromptCraftLang.t("Clearing area: ", "Очистка области: ") + pct + "%").formatted(Formatting.YELLOW), true);
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
