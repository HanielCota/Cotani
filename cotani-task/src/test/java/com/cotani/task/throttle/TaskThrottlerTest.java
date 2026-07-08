package com.cotani.task.throttle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import com.cotani.task.impl.chain.DefaultTaskChain;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskThrottlerTest {

    @Test
    @SuppressWarnings("unchecked")
    void testThrottleRetriesAndSucceeds() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        RateLimiter limiter = mock(RateLimiter.class);

        // First tryAcquire returns false (rate limited), second returns true
        when(limiter.tryAcquire()).thenReturn(false, true);
        when(limiter.retryDelay()).thenReturn(Duration.ofMillis(10));

        // When scheduler.supplyAsync is called, mock it to run immediately
        when(scheduler.supplyAsync(any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(0);
            try {
                Object result = supplier.get();
                return new DefaultTaskChain<>(CompletableFuture.completedFuture(result), scheduler);
            } catch (Throwable t) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(t);
                return new DefaultTaskChain<>(future, scheduler);
            }
        });

        // Mock delayAsync to return completed stage
        when(scheduler.delayAsync(any())).thenReturn(CompletableFuture.completedStage(null));

        // Mock scheduler.chain
        when(scheduler.chain(any(CompletionStage.class))).thenAnswer(invocation -> {
            CompletionStage<?> stage = invocation.getArgument(0);
            return new DefaultTaskChain<>(stage.toCompletableFuture(), scheduler);
        });

        TaskThrottler throttler = new TaskThrottler(scheduler);
        AtomicInteger counter = new AtomicInteger();

        TaskChain<Integer> chain = throttler.throttle(counter::incrementAndGet, limiter);
        Integer result = chain.toCompletionStage().toCompletableFuture().join();

        assertEquals(1, result);
        assertEquals(1, counter.get());
        verify(limiter, times(2)).tryAcquire();
        verify(scheduler).delayAsync(Duration.ofMillis(10));
    }
}
