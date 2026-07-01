package com.cotani.teleport.pending;

import com.cotani.task.api.SchedulerTask;
import com.cotani.teleport.api.PendingTeleportState;
import com.cotani.teleport.api.TeleportCancelReason;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class PendingTeleportStateMachine {
    private final PendingTeleportData data;
    private final AtomicReference<PendingTeleportState> state = new AtomicReference<>(PendingTeleportState.WAITING);
    private final AtomicReference<TeleportCancelReason> cancelReason = new AtomicReference<>();
    private volatile SchedulerTask task = SchedulerTask.noop();

    public PendingTeleportStateMachine(PendingTeleportData data) {
        this.data = data;
    }

    public PendingTeleportData data() {
        return data;
    }

    public PendingTeleportState state() {
        PendingTeleportState current = state.get();
        return current != null ? current : PendingTeleportState.CANCELLED;
    }

    public Optional<TeleportCancelReason> cancelReason() {
        return Optional.ofNullable(cancelReason.get());
    }

    public void attachTask(SchedulerTask task) {
        this.task = task;
    }

    public boolean markExecuting() {
        return state.compareAndSet(PendingTeleportState.WAITING, PendingTeleportState.EXECUTING);
    }

    public boolean markCompleted() {
        return state.compareAndSet(PendingTeleportState.EXECUTING, PendingTeleportState.COMPLETED);
    }

    public boolean cancel(TeleportCancelReason reason) {
        Objects.requireNonNull(reason, "reason");
        boolean changed = state.compareAndSet(PendingTeleportState.WAITING, PendingTeleportState.CANCELLED);
        if (changed) {
            cancelReason.set(reason);
            task.cancel();
        }
        return changed;
    }

    public boolean cancelExecution(TeleportCancelReason reason) {
        Objects.requireNonNull(reason, "reason");
        boolean changed = state.compareAndSet(PendingTeleportState.EXECUTING, PendingTeleportState.CANCELLED);
        if (changed) {
            cancelReason.set(reason);
            task.cancel();
        }
        return changed;
    }
}
