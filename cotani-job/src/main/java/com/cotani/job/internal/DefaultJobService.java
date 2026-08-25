package com.cotani.job.internal;

import com.cotani.api.InternalApi;
import com.cotani.job.api.JobExecutionContext;
import com.cotani.job.api.JobExecutionId;
import com.cotani.job.api.JobFailure;
import com.cotani.job.api.JobHandle;
import com.cotani.job.api.JobHandler;
import com.cotani.job.api.JobHandlerNotFoundException;
import com.cotani.job.api.JobId;
import com.cotani.job.api.JobRequest;
import com.cotani.job.api.JobSchedule;
import com.cotani.job.api.JobService;
import com.cotani.job.api.JobServiceOptions;
import com.cotani.task.api.DelayedTaskScheduler;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChainFactory;
import com.cotani.task.persistence.PersistentTask;
import com.cotani.task.persistence.PersistentTaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Default task-store-backed implementation of the job service. */
@InternalApi
public final class DefaultJobService implements JobService {
    private static final String TASK_PREFIX = "cotani-job:";
    private static final Logger LOGGER = Logger.getLogger(DefaultJobService.class.getName());

    private final DelayedTaskScheduler scheduler;
    private final TaskChainFactory chainFactory;
    private final PersistentTaskStore store;
    private final JobServiceOptions options;
    private final Clock clock;
    private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();
    private final Map<JobId, JobExecutionState> activeJobs = new ConcurrentHashMap<>();
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private volatile boolean closed;

    private DefaultJobService(
            PaperTaskScheduler scheduler, PersistentTaskStore store, JobServiceOptions options, Clock clock) {
        this(scheduler, scheduler, store, options, clock);
    }

    private DefaultJobService(
            DelayedTaskScheduler scheduler,
            TaskChainFactory chainFactory,
            PersistentTaskStore store,
            JobServiceOptions options,
            Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.chainFactory = Objects.requireNonNull(chainFactory, "chainFactory");
        this.store = Objects.requireNonNull(store, "store");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static JobService create(
            PaperTaskScheduler scheduler, PersistentTaskStore store, JobServiceOptions options) {
        return new DefaultJobService(scheduler, store, options, Clock.systemUTC());
    }

    static DefaultJobService create(
            PaperTaskScheduler scheduler, PersistentTaskStore store, JobServiceOptions options, Clock clock) {
        return new DefaultJobService(scheduler, store, options, clock);
    }

    @Override
    public void registerHandler(String name, JobHandler handler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        validateHandlerName(name);
        synchronized (lifecycleLock) {
            ensureOpen();
            handlers.put(name, handler);
        }
    }

    @Override
    public CompletionStage<JobHandle> scheduleAsync(JobRequest request) {
        Objects.requireNonNull(request, "request");
        ensureHandlerRegistered(request.handlerName());

        var state = new JobExecutionState(JobId.random());
        synchronized (lifecycleLock) {
            ensureOpen();
            activeJobs.put(state.id(), state);
        }

        var delay = initialDelay(request.schedule());
        var task = createTask(state.id(), request, delay);
        state.prepareForDispatch(task);

        var persisted = saveAsync(task);
        persisted.whenComplete((ignored, failure) -> state.completePersistence(failure));
        return persisted
                .thenCompose(ignored -> {
                    if (state.cancelled()) {
                        return completeCancelledState(state, task, null).thenApply(ignoredCompletion -> state);
                    }
                    if (closed) {
                        return CompletableFuture.completedFuture(state);
                    }
                    dispatch(state, task, delay);
                    return CompletableFuture.completedFuture(state);
                })
                .thenApply(result -> (JobHandle) result)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        activeJobs.remove(state.id(), state);
                    }
                });
    }

    @Override
    public CompletionStage<List<JobHandle>> recoverPendingAsync() {
        synchronized (lifecycleLock) {
            ensureOpen();
        }
        if (!recoveryInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(List.of());
        }

        try {
            return chainFactory
                    .supplyAsync(store::loadPending)
                    .toCompletionStage()
                    .thenApply(this::dispatchRecovered)
                    .whenComplete((ignored, failure) -> recoveryInProgress.set(false));
        } catch (RuntimeException failure) {
            recoveryInProgress.set(false);
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(JobId jobId) {
        Objects.requireNonNull(jobId, "jobId");
        var state = activeJobs.get(jobId);
        if (state == null) {
            return CompletableFuture.completedFuture(false);
        }

        var cancellation = state.requestCancellation();
        if (cancellation == JobExecutionState.CancellationRequest.NONE) {
            return state.terminalCompletion().thenApply(ignored -> true);
        }

        var cancellationCompletion = cancellation == JobExecutionState.CancellationRequest.RUNNING
                ? state.executionFinishedStage()
                : CompletableFuture.completedFuture(null);
        return state.persistedStage()
                .thenCompose(ignored -> cancellationCompletion)
                .thenCompose(ignored -> completeCancelledState(state, state.currentTask(), null))
                .thenApply(ignored -> true);
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closed) {
                return CompletableFuture.completedFuture(null);
            }
            closed = true;
        }

        activeJobs.values().forEach(JobExecutionState::stopForClose);
        activeJobs.clear();
        return CompletableFuture.completedFuture(null);
    }

    private List<JobHandle> dispatchRecovered(List<PersistentTask> pendingTasks) {
        var handles = new ArrayList<JobHandle>();
        pendingTasks.stream()
                .filter(DefaultJobService::isJobTask)
                .sorted(Comparator.comparing(this::dueAt))
                .limit(options.maxRecoveryBatch())
                .forEach(task -> recoverTask(task, handles));
        return List.copyOf(handles);
    }

    private void recoverTask(PersistentTask task, List<JobHandle> handles) {
        final JobEnvelope envelope;
        try {
            envelope = JobEnvelope.decode(task.payload(), new JobExecutionId(task.id()));
        } catch (IllegalArgumentException malformed) {
            reportPersistenceFailure(new JobId(task.id()), task.taskName(), malformed);
            return;
        }
        if (!handlers.containsKey(envelope.handlerName())) {
            reportPersistenceFailure(
                    envelope.jobId(), envelope.handlerName(), new JobHandlerNotFoundException(envelope.handlerName()));
            return;
        }

        var state = new JobExecutionState(envelope.jobId());
        state.prepareForDispatch(task);
        var existing = activeJobs.putIfAbsent(envelope.jobId(), state);
        if (existing != null) {
            if (!existing.isCurrentTask(task)) {
                markTaskCompletedOnce(task, existing, envelope);
            }
            return;
        }

        try {
            if (dispatch(state, task, remainingDelay(task))) {
                handles.add(state);
            } else {
                activeJobs.remove(envelope.jobId(), state);
            }
        } catch (RuntimeException schedulingFailure) {
            activeJobs.remove(envelope.jobId(), state);
            reportPersistenceFailure(envelope.jobId(), envelope.handlerName(), schedulingFailure);
        }
    }

    private void execute(PersistentTask task, JobExecutionState state) {
        if (closed || !state.tryStart(task)) {
            return;
        }

        final JobEnvelope envelope;
        try {
            envelope = JobEnvelope.decode(task.payload(), new JobExecutionId(task.id()));
        } catch (IllegalArgumentException malformed) {
            activeJobs.remove(state.id(), state);
            reportPersistenceFailure(new JobId(task.id()), task.taskName(), malformed);
            return;
        }
        var handler = handlers.get(envelope.handlerName());
        if (handler == null) {
            activeJobs.remove(state.id(), state);
            reportPersistenceFailure(
                    envelope.jobId(), envelope.handlerName(), new JobHandlerNotFoundException(envelope.handlerName()));
            return;
        }

        CompletionStage<Void> execution;
        try {
            execution = Objects.requireNonNull(
                    handler.executeAsync(new JobExecutionContext(
                            envelope.jobId(),
                            envelope.executionId(),
                            envelope.handlerName(),
                            envelope.attempt(),
                            envelope.payload())),
                    "job handler returned null");
        } catch (RuntimeException failure) {
            finishExecution(task, state, envelope, failure);
            return;
        }

        var executionFuture = execution.toCompletableFuture();
        var bounded = executionFuture
                .copy()
                .orTimeout(options.handlerTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        var _ = bounded.whenComplete((ignored, failure) -> {
            var cause = unwrap(failure);
            if (cause instanceof TimeoutException) {
                var _ = executionFuture.whenComplete((ignoredOriginal, originalFailure) ->
                        finishExecution(task, state, envelope, unwrap(originalFailure)));
                return;
            }
            finishExecution(task, state, envelope, cause);
        });
    }

    private void finishExecution(
            PersistentTask task, JobExecutionState state, JobEnvelope envelope, @Nullable Throwable failure) {
        state.completeExecution();
        if (state.cancelled()) {
            completeCancelledState(state, task, envelope);
            return;
        }
        if (closed) {
            return;
        }
        if (failure != null) {
            var willRetry = envelope.attempt() < envelope.retryPolicy().maxAttempts();
            notifyFailure(new JobFailure(
                    envelope.jobId(),
                    envelope.handlerName(),
                    envelope.attempt(),
                    envelope.retryPolicy().maxAttempts(),
                    willRetry,
                    failure));
            if (willRetry) {
                var delay = envelope.retryPolicy().delayBeforeNextAttempt(envelope.attempt());
                scheduleNext(state, task, envelope.nextAttempt(envelope.attempt() + 1), delay, task);
                return;
            }
        } else if (envelope.schedule() instanceof JobSchedule.Recurring recurring) {
            scheduleNext(state, task, envelope.nextOccurrence(), recurring.interval(), task);
            return;
        }
        completeTerminal(state, task, envelope);
    }

    private void scheduleNext(
            JobExecutionState state,
            PersistentTask current,
            JobEnvelope nextEnvelope,
            Duration delay,
            PersistentTask original) {
        if (closed || state.cancelled()) {
            return;
        }
        var next = new PersistentTask(
                java.util.UUID.randomUUID(),
                taskName(nextEnvelope.handlerName()),
                Instant.now(clock),
                delay,
                nextEnvelope.encode());
        saveAsync(next).whenComplete((ignored, failure) -> {
            if (failure != null) {
                state.abandon();
                activeJobs.remove(state.id(), state);
                reportPersistenceFailure(nextEnvelope.jobId(), nextEnvelope.handlerName(), requireFailure(failure));
                return;
            }
            if (state.cancelled()) {
                completeCancelledTransition(state, original, next, nextEnvelope);
                return;
            }
            if (closed) {
                return;
            }
            try {
                if (!dispatch(state, next, delay)) {
                    return;
                }
            } catch (RuntimeException schedulingFailure) {
                state.abandon();
                activeJobs.remove(state.id(), state);
                reportPersistenceFailure(nextEnvelope.jobId(), nextEnvelope.handlerName(), schedulingFailure);
                return;
            }
            markTaskCompletedOnce(current, state, nextEnvelope);
        });
    }

    private void completeTerminal(JobExecutionState state, PersistentTask task, JobEnvelope envelope) {
        markTaskCompletedOnce(task, state, envelope).whenComplete((ignored, failure) -> {
            if (failure != null) {
                state.abandon();
                activeJobs.remove(state.id(), state);
                return;
            }
            state.markTerminal();
            activeJobs.remove(state.id(), state);
        });
    }

    private CompletionStage<Void> completeCancelledState(
            JobExecutionState state, @Nullable PersistentTask task, @Nullable JobEnvelope envelope) {
        if (task == null) {
            state.markTerminal();
            activeJobs.remove(state.id(), state);
            return CompletableFuture.completedFuture(null);
        }
        return markTaskCompletedOnce(task, state, envelope).whenComplete((ignored, failure) -> {
            if (failure == null) {
                state.markTerminal();
                activeJobs.remove(state.id(), state);
            } else {
                state.abandon();
                activeJobs.remove(state.id(), state);
            }
        });
    }

    private void completeCancelledTransition(
            JobExecutionState state, PersistentTask original, PersistentTask next, JobEnvelope envelope) {
        markTaskCompletedOnce(next, state, envelope)
                .thenCompose(ignored -> markTaskCompletedOnce(original, state, envelope))
                .whenComplete((ignored, failure) -> {
                    if (failure == null) {
                        state.markTerminal();
                        activeJobs.remove(state.id(), state);
                    } else {
                        state.abandon();
                        activeJobs.remove(state.id(), state);
                    }
                });
    }

    private boolean dispatch(JobExecutionState state, PersistentTask task, Duration delay) {
        if (!state.prepareForDispatch(task) || closed) {
            return false;
        }
        var scheduled = scheduler.asyncLater(taskName(task.taskName()), () -> execute(task, state), delay);
        state.attachScheduledTask(task, scheduled);
        return true;
    }

    private CompletionStage<PersistentTask> saveAsync(PersistentTask task) {
        try {
            return chainFactory
                    .supplyAsync(() -> {
                        store.save(task);
                        return task;
                    })
                    .toCompletionStage();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<Void> markTaskCompletedOnce(
            PersistentTask task, JobExecutionState state, @Nullable JobEnvelope envelope) {
        var completion = state.beginTaskCompletion(task);
        if (completion.existing()) {
            return completion.future();
        }
        CompletionStage<Void> persisted;
        try {
            persisted = chainFactory
                    .supplyAsync(() -> {
                        store.markCompleted(task);
                        return (Void) null;
                    })
                    .toCompletionStage();
        } catch (RuntimeException failure) {
            reportPersistenceFailure(
                    envelope == null ? state.id() : envelope.jobId(),
                    envelope == null ? task.taskName() : envelope.handlerName(),
                    failure);
            completion.future().completeExceptionally(failure);
            return completion.future();
        }
        persisted.whenComplete((ignored, failure) -> {
            if (failure != null) {
                var id = envelope == null ? state.id() : envelope.jobId();
                var name = envelope == null ? task.taskName() : envelope.handlerName();
                reportPersistenceFailure(id, name, requireFailure(failure));
            }
        });
        persisted.whenComplete((ignored, failure) -> {
            if (failure == null) {
                completion.future().complete(null);
            } else {
                completion.future().completeExceptionally(failure);
            }
        });
        return completion.future();
    }

    private PersistentTask createTask(JobId jobId, JobRequest request, Duration delay) {
        return new PersistentTask(
                java.util.UUID.randomUUID(),
                taskName(request.handlerName()),
                Instant.now(clock),
                delay,
                JobEnvelope.from(jobId, request, 1).encode());
    }

    private static Duration initialDelay(JobSchedule schedule) {
        return switch (schedule) {
            case JobSchedule.Once once -> once.delay();
            case JobSchedule.Recurring recurring -> recurring.initialDelay();
        };
    }

    private Instant dueAt(PersistentTask task) {
        return task.scheduledAt().plus(task.delay());
    }

    private Duration remainingDelay(PersistentTask task) {
        var remaining = Duration.between(clock.instant(), dueAt(task));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private static boolean isJobTask(PersistentTask task) {
        return task.taskName().startsWith(TASK_PREFIX);
    }

    private static String taskName(String name) {
        return name.startsWith(TASK_PREFIX) ? name : TASK_PREFIX + name;
    }

    private static void validateHandlerName(String name) {
        if (name.isBlank() || name.length() > JobRequest.MAX_HANDLER_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid job handler name");
        }
        if (name.indexOf(':') >= 0 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid job handler name");
        }
    }

    private void ensureHandlerRegistered(String name) {
        if (!handlers.containsKey(name)) {
            throw new JobHandlerNotFoundException(name);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Job service is closed");
        }
    }

    private void notifyFailure(JobFailure failure) {
        try {
            options.failureListener().onFailure(failure);
        } catch (RuntimeException listenerFailure) {
            LOGGER.log(Level.WARNING, "Job failure listener threw an exception", listenerFailure);
        }
    }

    private void reportPersistenceFailure(@Nullable JobId jobId, String handlerName, Throwable failure) {
        var id = jobId == null ? JobId.random() : jobId;
        notifyFailure(new JobFailure(
                id,
                handlerName.startsWith(TASK_PREFIX) ? handlerName.substring(TASK_PREFIX.length()) : handlerName,
                1,
                1,
                false,
                failure));
    }

    private static Throwable requireFailure(@Nullable Throwable failure) {
        return Objects.requireNonNull(unwrap(failure), "failure");
    }

    private static @Nullable Throwable unwrap(@Nullable Throwable failure) {
        if (failure == null) {
            return null;
        }
        if (failure instanceof CompletionException && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }
}
