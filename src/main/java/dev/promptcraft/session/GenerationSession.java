package dev.promptcraft.session;

import dev.promptcraft.structure.PromptCraftStructure;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenerationSession {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile boolean destructionInProgress = false;
    private volatile boolean destructionComplete = false;
    private volatile boolean buildInProgress = false;

    private volatile CompletableFuture<?> httpFuture;
    private volatile Closeable activeStream;
    private volatile Process activeProcess;

    // --- Состояние свободного (AI-выбранного) размещения ---
    private volatile boolean ghostPending = false;
    private volatile PromptCraftStructure pendingStructure;

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public void markDestructionStarted() {
        destructionInProgress = true;
    }

    public void markDestructionComplete() {
        destructionInProgress = false;
        destructionComplete = true;
    }

    public boolean isDestructionInProgress() {
        return destructionInProgress;
    }

    public boolean isDestructionComplete() {
        return destructionComplete;
    }

    public void markBuildStarted() {
        buildInProgress = true;
    }

    public void markBuildFinished() {
        buildInProgress = false;
    }

    public boolean isBuildInProgress() {
        return buildInProgress;
    }

    public void setHttpFuture(CompletableFuture<?> future) {
        this.httpFuture = future;
    }

    public void setActiveStream(Closeable stream) {
        this.activeStream = stream;
    }

    public void setActiveProcess(Process process) {
        this.activeProcess = process;
    }

    public Process getActiveProcess() {
        return activeProcess;
    }

    public void abortHttpRequest() {
        CompletableFuture<?> future = this.httpFuture;
        if (future != null) {
            future.cancel(true);
        }

        Closeable stream = this.activeStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }

        Process process = this.activeProcess;
        if (process != null) {
            try {
                process.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
    }

    public void setGhostPending(boolean value) {
        this.ghostPending = value;
    }

    public boolean isGhostPending() {
        return ghostPending;
    }

    public void setPendingStructure(PromptCraftStructure structure) {
        this.pendingStructure = structure;
    }

    public PromptCraftStructure getPendingStructure() {
        return pendingStructure;
    }
}