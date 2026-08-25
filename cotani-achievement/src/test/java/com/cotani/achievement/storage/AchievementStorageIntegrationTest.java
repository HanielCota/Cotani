package com.cotani.achievement.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.achievement.api.AchievementId;
import com.cotani.achievement.api.AchievementProgress;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AchievementStorageIntegrationTest {
    @Test
    void roundTripsUnlockedProgressAndRejectsStaleRevision(@TempDir Path directory) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);
        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("achievement.db"))))
                .scheduler(scheduler)
                .migrations(StorageAchievementRepository.migrations().toArray(Migration[]::new))
                .build();
        try {
            storage.startAsync().toCompletableFuture().join();
            var repository = new StorageAchievementRepository(storage);
            var playerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            var achievementId = AchievementId.of("stone-master");
            var claimId = RewardClaimId.random();
            var initial = AchievementProgress.initial(playerId, achievementId);
            var unlocked = new AchievementProgress(
                    playerId,
                    achievementId,
                    true,
                    Optional.of(Instant.parse("2026-01-01T00:00:00Z")),
                    Optional.of(claimId),
                    initial.revision());

            var saved = repository.saveAsync(unlocked, 0).toCompletableFuture().join();

            assertEquals(1, saved.revision());
            assertEquals(
                    saved,
                    repository
                            .findAsync(playerId, achievementId)
                            .toCompletableFuture()
                            .join()
                            .orElseThrow());

            var failure = assertThrows(
                    CompletionException.class,
                    () -> repository.saveAsync(initial, 0).toCompletableFuture().join());
            assertTrue(failure.getCause() instanceof com.cotani.achievement.api.AchievementProgressConflictException);
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }
}
