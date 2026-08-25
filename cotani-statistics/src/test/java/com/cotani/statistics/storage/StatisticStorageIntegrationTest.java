package com.cotani.statistics.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticOperationId;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatisticStorageIntegrationTest {
    @Test
    void roundTripsAtomicIncrementsAndRankings(@TempDir Path directory) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);
        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("statistics.db"))))
                .scheduler(scheduler)
                .migrations(StorageStatisticRepository.migrations().toArray(Migration[]::new))
                .build();
        try {
            storage.startAsync().toCompletableFuture().join();
            var repository = new StorageStatisticRepository(storage);
            var statisticId = StatisticId.of("blocks-mined");
            var first = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            var second = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            var operationId = StatisticOperationId.random();

            var firstUpdate = repository
                    .incrementIdempotentlyAsync(first, statisticId, 4, java.time.Instant.now(), operationId)
                    .toCompletableFuture()
                    .join();
            var replay = repository
                    .incrementIdempotentlyAsync(first, statisticId, 4, java.time.Instant.now(), operationId)
                    .toCompletableFuture()
                    .join();
            var secondUpdate = repository
                    .incrementAsync(first, statisticId, 3, java.time.Instant.now())
                    .toCompletableFuture()
                    .join();
            repository
                    .incrementAsync(second, statisticId, 10, java.time.Instant.now())
                    .toCompletableFuture()
                    .join();

            assertEquals(0, firstUpdate.previousValue());
            assertTrue(firstUpdate.newlyApplied());
            assertFalse(replay.newlyApplied());
            assertEquals(firstUpdate.current(), replay.current());
            assertEquals(7, secondUpdate.current().value());
            assertEquals(
                    7,
                    repository
                            .findAsync(first, statisticId)
                            .toCompletableFuture()
                            .join()
                            .orElseThrow()
                            .value());
            var ranking =
                    repository.topAsync(statisticId, 2).toCompletableFuture().join();
            assertEquals(
                    List.of(second, first),
                    ranking.stream().map(entry -> entry.playerId()).toList());
            assertTrue(ranking.get(0).value() > ranking.get(1).value());

            var concurrentPlayer = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
            var concurrentOperations = new ArrayList<CompletableFuture<?>>();
            for (int index = 0; index < 20; index++) {
                concurrentOperations.add(repository
                        .incrementIdempotentlyAsync(
                                concurrentPlayer,
                                statisticId,
                                1,
                                java.time.Instant.now(),
                                StatisticOperationId.random())
                        .toCompletableFuture());
            }
            CompletableFuture.allOf(concurrentOperations.toArray(CompletableFuture[]::new))
                    .join();
            assertEquals(
                    20,
                    repository
                            .findAsync(concurrentPlayer, statisticId)
                            .toCompletableFuture()
                            .join()
                            .orElseThrow()
                            .value());
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }
}
