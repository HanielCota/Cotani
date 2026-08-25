package com.cotani.season.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.season.api.SeasonExperienceCommand;
import com.cotani.season.api.SeasonExperienceId;
import com.cotani.season.api.SeasonId;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeasonStorageIntegrationTest {
    @Test
    void appliesExperienceIdempotentlyAndPurgesTheLedger(@TempDir Path directory) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);
        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("season.db"))))
                .scheduler(scheduler)
                .migrations(StorageSeasonRepository.migrations().toArray(Migration[]::new))
                .build();
        try {
            storage.startAsync().toCompletableFuture().join();
            var repository = new StorageSeasonRepository(storage);
            var playerId = UUID.randomUUID();
            var seasonId = SeasonId.of("summer-2026");
            var operationId = SeasonExperienceId.random();
            var command = new SeasonExperienceCommand(
                    playerId, seasonId, 500, operationId, Instant.parse("2026-07-01T00:00:00Z"));

            var first = repository
                    .applyExperienceAsync(command)
                    .toCompletableFuture()
                    .join();
            var repeated = repository
                    .applyExperienceAsync(command)
                    .toCompletableFuture()
                    .join();

            assertEquals(500, first.experience());
            assertEquals(first, repeated);

            repository
                    .purgeExperienceOperationsBeforeAsync(Instant.parse("2026-08-01T00:00:00Z"))
                    .toCompletableFuture()
                    .join();
            var appliedAfterPurge = repository
                    .applyExperienceAsync(command)
                    .toCompletableFuture()
                    .join();

            assertEquals(1_000, appliedAfterPurge.experience());
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }
}
