package com.cotani.redis.internal;

import com.cotani.api.InternalApi;
import com.cotani.redis.exception.LockAcquisitionException;
import com.cotani.redis.lock.DistributedLock;
import com.cotani.redis.lock.DistributedLockService;
import com.cotani.redis.lock.LockKey;
import com.cotani.redis.lock.LockToken;
import com.cotani.task.api.PaperTaskScheduler;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link DistributedLockService} using atomic Redis SET NX and Lua release.
 */
@InternalApi
public final class DefaultDistributedLockService implements DistributedLockService {

    private static final String LOCK_PREFIX = "lock:";
    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private final Supplier<StatefulRedisConnection<String, String>> connectionSupplier;
    private final @Nullable PaperTaskScheduler scheduler;
    private final ScheduledExecutorService delayExecutor;

    public DefaultDistributedLockService(
            Supplier<StatefulRedisConnection<String, String>> connectionSupplier,
            @Nullable PaperTaskScheduler scheduler,
            ScheduledExecutorService delayExecutor) {
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "connectionSupplier");
        this.scheduler = scheduler;
        this.delayExecutor = Objects.requireNonNull(delayExecutor, "delayExecutor");
    }

    @Override
    public CompletionStage<Optional<DistributedLock>> tryAcquireAsync(LockKey key, Duration leaseTime) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(leaseTime, "leaseTime");
        if (leaseTime.isNegative() || leaseTime.isZero()) {
            throw new IllegalArgumentException("leaseTime must be positive");
        }

        var token = LockToken.random();
        var rawKey = LOCK_PREFIX + key.value();
        var setArgs = SetArgs.Builder.nx().px(leaseTime.toMillis());

        var commands = connectionSupplier.get().async();
        return commands.set(rawKey, token.value(), setArgs).thenApply(result -> {
            if ("OK".equalsIgnoreCase(result)) {
                DistributedLock lock = new DefaultDistributedLock(key, token, leaseTime, this::releaseLockAsync);
                return Optional.of(lock);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletionStage<DistributedLock> acquireAsync(LockKey key, Duration leaseTime, Duration waitTimeout) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(leaseTime, "leaseTime");
        Objects.requireNonNull(waitTimeout, "waitTimeout");
        if (waitTimeout.isNegative() || waitTimeout.isZero()) {
            throw new IllegalArgumentException("waitTimeout must be positive");
        }

        long deadlineNanos = System.nanoTime() + waitTimeout.toNanos();
        CompletableFuture<DistributedLock> resultFuture = new CompletableFuture<>();
        attemptAcquisitionLoop(key, leaseTime, deadlineNanos, 50, resultFuture);
        return resultFuture;
    }

    private void attemptAcquisitionLoop(
            LockKey key,
            Duration leaseTime,
            long deadlineNanos,
            long delayMillis,
            CompletableFuture<DistributedLock> resultFuture) {
        if (System.nanoTime() >= deadlineNanos) {
            resultFuture.completeExceptionally(
                    new LockAcquisitionException(key, "Timed out waiting to acquire lock: " + key.value()));
            return;
        }

        var _ = tryAcquireAsync(key, leaseTime).whenComplete((optionalLock, error) -> {
            if (error != null) {
                resultFuture.completeExceptionally(error);
                return;
            }

            if (optionalLock.isPresent()) {
                resultFuture.complete(optionalLock.get());
                return;
            }

            long nextDelay = Math.min(delayMillis * 2, 500);
            if (scheduler != null) {
                var _ = scheduler
                        .delayAsync(Duration.ofMillis(delayMillis))
                        .thenRun(() -> attemptAcquisitionLoop(key, leaseTime, deadlineNanos, nextDelay, resultFuture));
                return;
            }

            var _ = delayExecutor.schedule(
                    () -> attemptAcquisitionLoop(key, leaseTime, deadlineNanos, nextDelay, resultFuture),
                    delayMillis,
                    TimeUnit.MILLISECONDS);
        });
    }

    @Override
    public <T> CompletionStage<T> withLockAsync(LockKey key, Duration leaseTime, Supplier<CompletionStage<T>> action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(leaseTime, "leaseTime");
        Objects.requireNonNull(action, "action");

        return tryAcquireAsync(key, leaseTime).thenCompose(optionalLock -> {
            if (optionalLock.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new LockAcquisitionException(key, "Could not acquire lock for key: " + key.value()));
            }

            DistributedLock lock = optionalLock.get();
            CompletionStage<T> actionStage;
            try {
                actionStage = action.get();
                if (actionStage == null) {
                    actionStage =
                            CompletableFuture.failedFuture(new NullPointerException("action returned null stage"));
                }
            } catch (Exception e) {
                return lock.releaseAsync().thenCompose(_ -> CompletableFuture.failedFuture(e));
            }

            CompletableFuture<T> response = new CompletableFuture<>();
            var _ = actionStage.whenComplete((result, error) -> {
                var _ = lock.releaseAsync().whenComplete((_, releaseError) -> {
                    if (error != null) {
                        if (releaseError != null) {
                            error.addSuppressed(releaseError);
                        }
                        response.completeExceptionally(error);
                        return;
                    }
                    if (releaseError != null) {
                        response.completeExceptionally(releaseError);
                        return;
                    }
                    response.complete(result);
                });
            });
            return response;
        });
    }

    private static final String RENEW_LUA = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """;

    @Override
    public <T> CompletionStage<T> withWatchdogLockAsync(
            LockKey key, Duration initialLeaseTime, Supplier<CompletionStage<T>> action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(initialLeaseTime, "initialLeaseTime");
        Objects.requireNonNull(action, "action");

        return tryAcquireAsync(key, initialLeaseTime).thenCompose(optionalLock -> {
            if (optionalLock.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new LockAcquisitionException(key, "Could not acquire lock for key: " + key.value()));
            }

            DistributedLock lock = optionalLock.get();
            AtomicBoolean running = new AtomicBoolean(true);
            long renewalDelayMillis = Math.max(100L, initialLeaseTime.toMillis() / 3L);
            scheduleWatchdogRenewal(key, lock.token(), initialLeaseTime, renewalDelayMillis, running);

            CompletionStage<T> actionStage;
            try {
                actionStage = action.get();
                if (actionStage == null) {
                    actionStage =
                            CompletableFuture.failedFuture(new NullPointerException("action returned null stage"));
                }
            } catch (Exception e) {
                running.set(false);
                return lock.releaseAsync().thenCompose(_ -> CompletableFuture.failedFuture(e));
            }

            CompletableFuture<T> response = new CompletableFuture<>();
            var _ = actionStage.whenComplete((result, error) -> {
                running.set(false);
                var _ = lock.releaseAsync().whenComplete((_, releaseError) -> {
                    if (error != null) {
                        if (releaseError != null) {
                            error.addSuppressed(releaseError);
                        }
                        response.completeExceptionally(error);
                        return;
                    }
                    if (releaseError != null) {
                        response.completeExceptionally(releaseError);
                        return;
                    }
                    response.complete(result);
                });
            });
            return response;
        });
    }

    private void scheduleWatchdogRenewal(
            LockKey key, LockToken token, Duration leaseTime, long delayMillis, AtomicBoolean running) {
        if (!running.get()) {
            return;
        }

        Runnable renewalAction = () -> {
            if (!running.get()) {
                return;
            }
            var _ = renewLockAsync(key, token, leaseTime).whenComplete((renewed, _) -> {
                if (Boolean.TRUE.equals(renewed) && running.get()) {
                    scheduleWatchdogRenewal(key, token, leaseTime, delayMillis, running);
                }
            });
        };

        if (scheduler != null) {
            var _ = scheduler.asyncLater(renewalAction, Duration.ofMillis(delayMillis));
            return;
        }
        var _ = delayExecutor.schedule(renewalAction, delayMillis, TimeUnit.MILLISECONDS);
    }

    public CompletionStage<Boolean> renewLockAsync(LockKey key, LockToken token, Duration leaseTime) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(leaseTime, "leaseTime");
        var rawKey = LOCK_PREFIX + key.value();
        var commands = connectionSupplier.get().async();
        String[] keys = new String[] {rawKey};
        String[] values = new String[] {token.value(), String.valueOf(leaseTime.toMillis())};

        return commands.eval(RENEW_LUA, ScriptOutputType.INTEGER, keys, values).thenApply(res -> {
            if (res instanceof Number num) {
                return num.longValue() == 1L;
            }
            return false;
        });
    }

    private CompletionStage<Void> releaseLockAsync(LockKey key, LockToken token) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        var rawKey = LOCK_PREFIX + key.value();
        var commands = connectionSupplier.get().async();
        String[] keys = new String[] {rawKey};
        String[] values = new String[] {token.value()};

        return commands.eval(UNLOCK_LUA, ScriptOutputType.INTEGER, keys, values).thenApply(_ -> (Void) null);
    }
}
