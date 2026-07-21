package com.cotani.storage.future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.task.api.PaperTaskScheduler;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class OptionalStorageFutureTest {

    @Test
    void fallbackUsesSupplierWhenEmpty() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        StorageFuture<Optional<String>> source = StorageFuture.completed(Optional.empty(), scheduler);
        OptionalStorageFuture<String> future = new OptionalStorageFuture<>(source, scheduler);

        StorageFuture<String> fallback = future.fallback(() -> "default");

        assertEquals(
                "default", fallback.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void fallbackKeepsValueWhenPresent() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        StorageFuture<Optional<String>> source = StorageFuture.completed(Optional.of("value"), scheduler);
        OptionalStorageFuture<String> future = new OptionalStorageFuture<>(source, scheduler);

        StorageFuture<String> fallback = future.fallback(() -> "default");

        assertEquals("value", fallback.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void fallbackFutureUsesSupplierWhenEmpty() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        StorageFuture<Optional<String>> source = StorageFuture.completed(Optional.empty(), scheduler);
        OptionalStorageFuture<String> future = new OptionalStorageFuture<>(source, scheduler);

        StorageFuture<String> fallback =
                future.fallbackFuture(() -> StorageFuture.completed("async-default", scheduler));

        assertEquals(
                "async-default",
                fallback.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void fallbackFutureKeepsValueWhenPresent() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        StorageFuture<Optional<String>> source = StorageFuture.completed(Optional.of("value"), scheduler);
        OptionalStorageFuture<String> future = new OptionalStorageFuture<>(source, scheduler);

        StorageFuture<String> fallback =
                future.fallbackFuture(() -> StorageFuture.completed("async-default", scheduler));

        assertEquals("value", fallback.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void toCompletionStageReturnsUnderlyingStage() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        StorageFuture<Optional<Integer>> source = StorageFuture.completed(Optional.of(42), scheduler);
        OptionalStorageFuture<Integer> future = new OptionalStorageFuture<>(source, scheduler);

        assertEquals(
                Optional.of(42),
                future.toCompletionStage().toCompletableFuture().join());
    }

    @Test
    void sourceReturnsWrappedFuture() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        StorageFuture<Optional<Integer>> source = StorageFuture.completed(Optional.of(42), scheduler);
        OptionalStorageFuture<Integer> future = new OptionalStorageFuture<>(source, scheduler);

        assertSame(source, future.source());
    }
}
