package com.cotani.redis.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.redis.lock.DistributedLock;
import com.cotani.redis.lock.LockKey;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultDistributedLockServiceTest {

    private StatefulRedisConnection<String, String> connection;
    private RedisAsyncCommands<String, String> commands;
    private DefaultDistributedLockService lockService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connection = mock(StatefulRedisConnection.class);
        commands = mock(RedisAsyncCommands.class);
        when(connection.async()).thenReturn(commands);
        lockService = new DefaultDistributedLockService(() -> connection, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAcquireLockWhenRedisReturnsOk() {
        var key = LockKey.of("user:123");
        var future = mock(RedisFuture.class);
        when(future.thenApply(any())).thenAnswer(invocation -> {
            java.util.function.Function<String, Optional<DistributedLock>> fn = invocation.getArgument(0);
            return CompletableFuture.completedFuture(fn.apply("OK"));
        });
        when(commands.set(eq("lock:user:123"), anyString(), any(SetArgs.class))).thenReturn(future);

        var optionalLock = lockService
                .tryAcquireAsync(key, Duration.ofSeconds(5))
                .toCompletableFuture()
                .join();

        assertTrue(optionalLock.isPresent());
        assertEquals(key, optionalLock.get().key());
    }

    @Test
    @SuppressWarnings({"unchecked", "DataFlowIssue", "NullAway"})
    void shouldReturnEmptyWhenRedisReturnsNull() {
        var key = LockKey.of("user:123");
        var future = mock(RedisFuture.class);
        when(future.thenApply(any())).thenAnswer(invocation -> {
            java.util.function.Function<String, Optional<DistributedLock>> fn = invocation.getArgument(0);
            String nullResult = null;
            return CompletableFuture.completedFuture(fn.apply(nullResult));
        });
        when(commands.set(eq("lock:user:123"), anyString(), any(SetArgs.class))).thenReturn(future);

        var optionalLock = lockService
                .tryAcquireAsync(key, Duration.ofSeconds(5))
                .toCompletableFuture()
                .join();

        assertFalse(optionalLock.isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteWithLockAndReleaseAutomatically() {
        var key = LockKey.of("user:123");
        var setFuture = mock(RedisFuture.class);
        when(setFuture.thenApply(any())).thenAnswer(invocation -> {
            java.util.function.Function<String, Optional<DistributedLock>> fn = invocation.getArgument(0);
            return CompletableFuture.completedFuture(fn.apply("OK"));
        });
        when(commands.set(eq("lock:user:123"), anyString(), any(SetArgs.class))).thenReturn(setFuture);

        var evalFuture = mock(RedisFuture.class);
        when(evalFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(commands.eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class)))
                .thenReturn(evalFuture);

        var executed = new AtomicBoolean();
        String result = lockService
                .withLockAsync(key, Duration.ofSeconds(5), () -> {
                    executed.set(true);
                    return CompletableFuture.completedFuture("Success");
                })
                .toCompletableFuture()
                .join();

        assertEquals("Success", result);
        assertTrue(executed.get());
        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReleaseLockWhenActionThrowsSynchronously() {
        var key = LockKey.of("user:fail_sync");
        var setFuture = mock(RedisFuture.class);
        when(setFuture.thenApply(any())).thenAnswer(invocation -> {
            java.util.function.Function<String, Optional<DistributedLock>> fn = invocation.getArgument(0);
            return CompletableFuture.completedFuture(fn.apply("OK"));
        });
        when(commands.set(eq("lock:user:fail_sync"), anyString(), any(SetArgs.class)))
                .thenReturn(setFuture);

        var evalFuture = mock(RedisFuture.class);
        when(evalFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(commands.eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class)))
                .thenReturn(evalFuture);

        org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> lockService
                        .withLockAsync(key, Duration.ofSeconds(5), () -> {
                            throw new IllegalStateException("Simulated synchronous error");
                        })
                        .toCompletableFuture()
                        .join());

        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReleaseLockWhenActionCompletesExceptionally() {
        var key = LockKey.of("user:fail_async");
        var setFuture = mock(RedisFuture.class);
        when(setFuture.thenApply(any())).thenAnswer(invocation -> {
            java.util.function.Function<String, Optional<DistributedLock>> fn = invocation.getArgument(0);
            return CompletableFuture.completedFuture(fn.apply("OK"));
        });
        when(commands.set(eq("lock:user:fail_async"), anyString(), any(SetArgs.class)))
                .thenReturn(setFuture);

        var evalFuture = mock(RedisFuture.class);
        when(evalFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(commands.eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class)))
                .thenReturn(evalFuture);

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> lockService
                        .withLockAsync(
                                key,
                                Duration.ofSeconds(5),
                                () -> CompletableFuture.failedFuture(new RuntimeException("Simulated async failure")))
                        .toCompletableFuture()
                        .join());

        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteWithWatchdogLockAndRelease() {
        var key = LockKey.of("user:watchdog");
        var setFuture = mock(RedisFuture.class);
        when(setFuture.thenApply(any())).thenAnswer(invocation -> {
            java.util.function.Function<String, Optional<DistributedLock>> fn = invocation.getArgument(0);
            return CompletableFuture.completedFuture(fn.apply("OK"));
        });
        when(commands.set(eq("lock:user:watchdog"), anyString(), any(SetArgs.class)))
                .thenReturn(setFuture);

        var evalFuture = mock(RedisFuture.class);
        when(evalFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(commands.eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class)))
                .thenReturn(evalFuture);

        String result = lockService
                .withWatchdogLockAsync(
                        key, Duration.ofSeconds(5), () -> CompletableFuture.completedFuture("WatchdogDone"))
                .toCompletableFuture()
                .join();

        assertEquals("WatchdogDone", result);
        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class));
    }
}
