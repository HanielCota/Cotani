package com.cotani.task.chain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.RetryPolicy;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskChain;
import com.cotani.task.exception.TaskTimeoutException;
import com.cotani.task.internal.chain.DefaultTaskChain;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultTaskChainTest {
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    @BeforeEach
    void setUp() {
        when(scheduler.asyncExecutor()).thenReturn(Runnable::run);
        when(scheduler.chain(any()))
                .thenAnswer(invocation -> DefaultTaskChain.create(invocation.getArgument(0), scheduler));
    }

    @Test
    void onStartRunsAtChainStart() throws Exception {
        AtomicBoolean started = new AtomicBoolean(false);
        DefaultTaskChain<String> chain = DefaultTaskChain.create(CompletableFuture.completedFuture("value"), scheduler);

        when(scheduler.supplyAsync(any(), any())).thenAnswer(invocation -> {
            var supplier = invocation.<Supplier<Object>>getArgument(1);
            return DefaultTaskChain.create(CompletableFuture.completedFuture(supplier.get()), scheduler);
        });

        chain.onStart(() -> started.set(true))
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertTrue(started.get());
    }

    @Test
    void onCompleteRunsOnSuccess() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        DefaultTaskChain<String> chain = DefaultTaskChain.create(CompletableFuture.completedFuture("value"), scheduler);

        chain.onComplete(() -> completed.set(true));
        chain.toCompletionStage().toCompletableFuture().get();

        assertTrue(completed.get());
    }

    @Test
    void onCompleteRunsOnFailure() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        DefaultTaskChain<String> chain =
                DefaultTaskChain.create(CompletableFuture.failedFuture(new RuntimeException("boom")), scheduler);

        chain.onComplete(() -> completed.set(true));

        assertFalse(chain.toCompletionStage().toCompletableFuture().isCancelled());
        assertTrue(completed.get());
    }

    @Test
    void onCancelRunsWhenCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultTaskChain<String> chain = DefaultTaskChain.create(future, scheduler);

        chain.onCancel(() -> cancelled.set(true));
        chain.cancel();

        assertTrue(cancelled.get());
    }

    @Test
    void timeoutFailsWhenFutureDoesNotComplete() {
        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultTaskChain<String> chain = DefaultTaskChain.create(future, scheduler);

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> chain.timeout(Duration.ofMillis(10))
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get());

        assertInstanceOf(TaskTimeoutException.class, exception.getCause());
    }

    @Test
    void timeoutDoesNotAffectFastChain() throws Exception {
        DefaultTaskChain<String> chain = DefaultTaskChain.create(CompletableFuture.completedFuture("value"), scheduler);

        String result = chain.timeout(Duration.ofSeconds(1))
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals("value", result);
    }

    @Test
    void timeoutPreservesOriginalException() {
        IllegalStateException cause = new IllegalStateException("db failed");
        DefaultTaskChain<String> chain = DefaultTaskChain.create(CompletableFuture.failedFuture(cause), scheduler);

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> chain.timeout(Duration.ofSeconds(1))
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get());

        assertSame(cause, exception.getCause());
    }

    @Test
    void timeoutDoesNotCompleteSharedSource() throws Exception {
        CompletableFuture<String> source = new CompletableFuture<>();
        DefaultTaskChain<String> chain = DefaultTaskChain.create(source, scheduler);

        assertThrows(
                ExecutionException.class,
                () -> chain.timeout(Duration.ofMillis(10))
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get());

        assertFalse(source.isDone());
        source.complete("late-success");
        assertEquals("late-success", source.get());
    }

    @Test
    void timeoutRejectsNonPositiveAndExcessiveDurations() {
        DefaultTaskChain<String> chain = DefaultTaskChain.create(new CompletableFuture<>(), scheduler);

        assertThrows(IllegalArgumentException.class, () -> chain.timeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> chain.timeout(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> chain.timeout(Duration.ofSeconds(Long.MAX_VALUE)));
    }

    @Test
    void retryReexecutesRepeatableSupplierExactNumberOfTimes() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<CompletableFuture<String>> factory = () -> {
            int attempt = attempts.incrementAndGet();
            return attempt < 3
                    ? CompletableFuture.failedFuture(new IllegalStateException("attempt-" + attempt))
                    : CompletableFuture.completedFuture("ok");
        };
        when(scheduler.asyncLater(any(String.class), any(Runnable.class), any(Duration.class)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(1).run();
                    return SchedulerTask.noop();
                });

        DefaultTaskChain<String> chain = DefaultTaskChain.create(factory.get(), scheduler, factory);
        String result = chain.retry(RetryPolicy.fixed(3, Duration.ZERO))
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void retryRejectsExternalNonRepeatableStage() {
        DefaultTaskChain<String> chain =
                DefaultTaskChain.create(CompletableFuture.failedFuture(new IllegalStateException("boom")), scheduler);

        assertThrows(IllegalStateException.class, () -> chain.retry(RetryPolicy.fixed(3, Duration.ZERO)));
    }

    @Test
    void cancellingRetryCancelsScheduledAttempt() {
        SchedulerTask pending = mock(SchedulerTask.class);
        when(scheduler.asyncLater(any(String.class), any(Runnable.class), any(Duration.class)))
                .thenReturn(pending);
        Supplier<CompletableFuture<String>> factory =
                () -> CompletableFuture.failedFuture(new IllegalStateException("boom"));
        DefaultTaskChain<String> chain = DefaultTaskChain.create(factory.get(), scheduler, factory);

        TaskChain<String> retried = chain.retry(RetryPolicy.fixed(3, Duration.ofSeconds(1)));
        retried.cancel();

        Mockito.verify(pending).cancel();
    }

    @Test
    void allOfCollectsResults() throws Exception {
        TaskChain<String> a = DefaultTaskChain.create(CompletableFuture.completedFuture("a"), scheduler);
        TaskChain<String> b = DefaultTaskChain.create(CompletableFuture.completedFuture("b"), scheduler);

        List<String> result = TaskChain.allOf(scheduler, a, b)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(2, result.size());
        assertEquals("a", result.getFirst());
        assertEquals("b", result.getLast());
    }

    @Test
    void allOfHandlesNullAndVoidResultsSafely() throws Exception {
        TaskChain<Void> a = DefaultTaskChain.create(CompletableFuture.completedFuture(null), scheduler);
        TaskChain<Void> b = DefaultTaskChain.create(CompletableFuture.completedFuture(null), scheduler);

        List<Void> result = TaskChain.allOf(scheduler, a, b)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(2, result.size());
        assertNull(result.getFirst());
        assertNull(result.getLast());
    }

    @Test
    void anyOfReturnsFirstResult() throws Exception {
        TaskChain<String> a = DefaultTaskChain.create(CompletableFuture.completedFuture("a"), scheduler);
        TaskChain<String> b = DefaultTaskChain.create(CompletableFuture.completedFuture("b"), scheduler);

        String result = TaskChain.anyOf(scheduler, a, b)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertTrue(result.equals("a") || result.equals("b"));
    }

    @Test
    void anyOfRejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> TaskChain.anyOf(scheduler));
    }

    @Test
    void filterKeepsMatchingValue() throws Exception {
        DefaultTaskChain<Integer> chain = DefaultTaskChain.create(CompletableFuture.completedFuture(10), scheduler);

        Integer result = chain.filter(value -> value > 5)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(10, result);
    }

    @Test
    void filterRejectsNonMatchingValue() {
        DefaultTaskChain<Integer> chain = DefaultTaskChain.create(CompletableFuture.completedFuture(2), scheduler);

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> chain.filter(value -> value > 5)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get());

        assertInstanceOf(NoSuchElementException.class, exception.getCause());
    }

    @Test
    void flatMapFlattensInnerChain() throws Exception {
        DefaultTaskChain<Integer> chain = DefaultTaskChain.create(CompletableFuture.completedFuture(2), scheduler);

        Integer result = chain.flatMap(
                        value -> DefaultTaskChain.create(CompletableFuture.completedFuture(value * 3), scheduler))
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(6, result);
    }
}
