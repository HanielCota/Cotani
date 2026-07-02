package com.cotani.storage.future;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.storage.error.QueryError;
import com.cotani.storage.error.StorageException;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class StorageFutureTest {

    @Test
    void completedReturnsCompletedFuture() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        var future = StorageFuture.completed("value", scheduler);
        assertFalse(future.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        assertEquals("value", future.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void completedAcceptsNull() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        var future = StorageFuture.completed(null, scheduler);
        assertNull(future.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void failedReturnsFailedFuture() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        var error = new StorageException(new QueryError("test", null));
        var future = StorageFuture.failed(error, scheduler);
        assertTrue(future.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void onFailureRunsOnError() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        org.mockito.Mockito.when(scheduler.async(org.mockito.Mockito.any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });
        var future = new StorageFuture<>(CompletableFuture.failedFuture(new RuntimeException("fail")), scheduler);
        var called = new AtomicBoolean();
        future.onFailure(_ -> called.set(true));
        future.toCompletionStage()
                .toCompletableFuture()
                .exceptionally(_ -> null)
                .join();
        assertTrue(called.get());
    }

    @Test
    void mapTransformsValue() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        var future = StorageFuture.completed(42, scheduler);
        var mapped = future.map(n -> n * 2);
        assertEquals(84, mapped.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void flatMapChainsFutures() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        var future = StorageFuture.completed(2, scheduler);
        var chained = future.flatMap(n -> StorageFuture.completed(n * 3, scheduler));
        assertEquals(6, chained.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void fallbackUsesSupplierOnFailure() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        var future = new StorageFuture<>(CompletableFuture.failedFuture(new RuntimeException("fail")), scheduler);
        var fallback = future.fallback(() -> 42);
        assertEquals(42, fallback.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void toCompletionStageMatchesRawValue() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        var future = StorageFuture.completed("hello", scheduler);
        assertEquals("hello", future.toCompletionStage().toCompletableFuture().join());
    }
}
