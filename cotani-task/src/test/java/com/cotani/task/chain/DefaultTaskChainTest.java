package com.cotani.task.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import com.cotani.task.exception.TaskTimeoutException;
import com.cotani.task.impl.chain.DefaultTaskChain;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultTaskChainTest {

    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    @Test
    void onStartRunsImmediately() {
        AtomicBoolean started = new AtomicBoolean(false);
        DefaultTaskChain<String> chain = new DefaultTaskChain<>(CompletableFuture.completedFuture("value"), scheduler);

        chain.onStart(() -> started.set(true));

        assertTrue(started.get());
    }

    @Test
    void onCompleteRunsOnSuccess() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        DefaultTaskChain<String> chain = new DefaultTaskChain<>(CompletableFuture.completedFuture("value"), scheduler);

        chain.onComplete(() -> completed.set(true));
        chain.future().get();

        assertTrue(completed.get());
    }

    @Test
    void onCompleteRunsOnFailure() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        DefaultTaskChain<String> chain =
                new DefaultTaskChain<>(CompletableFuture.failedFuture(new RuntimeException("boom")), scheduler);

        chain.onComplete(() -> completed.set(true));

        assertFalse(chain.future().isCancelled());
        assertTrue(completed.get());
    }

    @Test
    void onCancelRunsWhenCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultTaskChain<String> chain = new DefaultTaskChain<>(future, scheduler);

        chain.onCancel(() -> cancelled.set(true));
        chain.cancel();

        assertTrue(cancelled.get());
    }

    @Test
    void timeoutFailsWhenFutureDoesNotComplete() {
        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultTaskChain<String> chain = new DefaultTaskChain<>(future, scheduler);

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> chain.timeout(Duration.ofMillis(10)).future().get());

        assertTrue(exception.getCause() instanceof TaskTimeoutException);
    }

    @Test
    void timeoutDoesNotAffectFastChain() throws Exception {
        DefaultTaskChain<String> chain = new DefaultTaskChain<>(CompletableFuture.completedFuture("value"), scheduler);

        String result = chain.timeout(Duration.ofSeconds(1)).future().get();

        assertTrue(result.equals("value"));
    }

    @Test
    void allOfCollectsResults() throws Exception {
        TaskChain<String> a = new DefaultTaskChain<>(CompletableFuture.completedFuture("a"), scheduler);
        TaskChain<String> b = new DefaultTaskChain<>(CompletableFuture.completedFuture("b"), scheduler);

        List<String> result = TaskChain.allOf(scheduler, a, b).future().get();

        assertEquals(2, result.size());
        assertEquals("a", result.getFirst());
        assertEquals("b", result.getLast());
    }

    @Test
    void anyOfReturnsFirstResult() throws Exception {
        TaskChain<String> a = new DefaultTaskChain<>(CompletableFuture.completedFuture("a"), scheduler);
        TaskChain<String> b = new DefaultTaskChain<>(CompletableFuture.completedFuture("b"), scheduler);

        String result = TaskChain.anyOf(scheduler, a, b).future().get();

        assertTrue(result.equals("a") || result.equals("b"));
    }

    @Test
    void anyOfRejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> TaskChain.anyOf(scheduler));
    }

    @Test
    void filterKeepsMatchingValue() throws Exception {
        DefaultTaskChain<Integer> chain = new DefaultTaskChain<>(CompletableFuture.completedFuture(10), scheduler);

        Integer result = chain.filter(value -> value > 5).future().get();

        assertEquals(10, result);
    }

    @Test
    void filterRejectsNonMatchingValue() {
        DefaultTaskChain<Integer> chain = new DefaultTaskChain<>(CompletableFuture.completedFuture(2), scheduler);

        ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> chain.filter(value -> value > 5).future().get());

        assertTrue(exception.getCause() instanceof NoSuchElementException);
    }

    @Test
    void flatMapFlattensInnerChain() throws Exception {
        DefaultTaskChain<Integer> chain = new DefaultTaskChain<>(CompletableFuture.completedFuture(2), scheduler);

        Integer result = chain.flatMap(
                        value -> new DefaultTaskChain<>(CompletableFuture.completedFuture(value * 3), scheduler))
                .future()
                .get();

        assertEquals(6, result);
    }
}
