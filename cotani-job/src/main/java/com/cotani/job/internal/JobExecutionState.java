package com.cotani.job.internal;

import com.cotani.job.api.JobHandle;
import com.cotani.job.api.JobId;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.persistence.PersistentTask;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe lifecycle state for one persisted job.
 *
 * <p>The service owns orchestration; this type owns only cancellation, execution and terminal
 * state transitions for a single job.
 */
final class JobExecutionState implements JobHandle {

    private final JobId id;
    private final CompletableFuture<Void> persisted = new CompletableFuture<>();
    private final CompletableFuture<Boolean> terminalCompletion = new CompletableFuture<>();
    private final Map<UUID, CompletableFuture<Void>> taskCompletions = new ConcurrentHashMap<>();

    private Phase phase = Phase.SCHEDULED;
    private @Nullable PersistentTask currentTask;
    private @Nullable SchedulerTask scheduledTask;
    private CompletableFuture<Void> executionCompletion = CompletableFuture.completedFuture(null);

    JobExecutionState(JobId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public JobId id() {
        return id;
    }

    @Override
    public boolean cancel() {
        return requestCancellation() != CancellationRequest.NONE;
    }

    @Override
    public synchronized boolean cancelled() {
        return phase == Phase.CANCELLATION_REQUESTED || phase == Phase.TERMINAL;
    }

    synchronized CancellationRequest requestCancellation() {
        if (phase == Phase.TERMINAL || phase == Phase.CLOSED || phase == Phase.CANCELLATION_REQUESTED) {
            return CancellationRequest.NONE;
        }
        var request = phase == Phase.RUNNING ? CancellationRequest.RUNNING : CancellationRequest.SCHEDULED;
        phase = Phase.CANCELLATION_REQUESTED;
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
        return request;
    }

    synchronized boolean prepareForDispatch(PersistentTask task) {
        if (phase == Phase.CANCELLATION_REQUESTED || phase == Phase.CLOSED || phase == Phase.TERMINAL) {
            return false;
        }
        currentTask = task;
        phase = Phase.SCHEDULED;
        return true;
    }

    synchronized void attachScheduledTask(PersistentTask task, SchedulerTask scheduled) {
        if (phase == Phase.SCHEDULED && Objects.equals(currentTask, task)) {
            scheduledTask = scheduled;
            return;
        }
        scheduled.cancel();
    }

    synchronized boolean tryStart(PersistentTask task) {
        if (phase != Phase.SCHEDULED || !Objects.equals(currentTask, task)) {
            return false;
        }
        phase = Phase.RUNNING;
        executionCompletion = new CompletableFuture<>();
        scheduledTask = null;
        return true;
    }

    synchronized boolean isCurrentTask(PersistentTask task) {
        return Objects.equals(currentTask, task) && phase != Phase.TERMINAL;
    }

    synchronized @Nullable PersistentTask currentTask() {
        return currentTask;
    }

    synchronized CompletionStage<Void> executionFinishedStage() {
        return executionCompletion;
    }

    synchronized void completeExecution() {
        executionCompletion.complete(null);
    }

    synchronized void completePersistence(@Nullable Throwable failure) {
        if (failure == null) {
            persisted.complete(null);
        } else {
            persisted.completeExceptionally(unwrap(failure));
        }
    }

    CompletionStage<Void> persistedStage() {
        return persisted;
    }

    CompletionRegistration beginTaskCompletion(PersistentTask task) {
        var created = new CompletableFuture<Void>();
        var existing = taskCompletions.putIfAbsent(task.id(), created);
        return existing == null
                ? new CompletionRegistration(created, false)
                : new CompletionRegistration(existing, true);
    }

    synchronized void stopForClose() {
        if (phase == Phase.SCHEDULED) {
            phase = Phase.CLOSED;
            if (scheduledTask != null) {
                scheduledTask.cancel();
                scheduledTask = null;
            }
        }
    }

    synchronized void abandon() {
        if (phase != Phase.TERMINAL) {
            phase = Phase.CLOSED;
        }
    }

    synchronized void markTerminal() {
        phase = Phase.TERMINAL;
        terminalCompletion.complete(true);
    }

    CompletionStage<Boolean> terminalCompletion() {
        return terminalCompletion;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
    }

    enum CancellationRequest {
        NONE,
        SCHEDULED,
        RUNNING
    }

    private enum Phase {
        SCHEDULED,
        RUNNING,
        CANCELLATION_REQUESTED,
        CLOSED,
        TERMINAL
    }

    record CompletionRegistration(CompletableFuture<Void> future, boolean existing) {}
}
