package com.cotani.quest.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestObjectiveId;
import com.cotani.quest.api.QuestProgress;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestStorageIntegrationTest {
    @Test
    void roundTripsProgressAndRejectsStaleRevision(@TempDir Path directory) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);
        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("quest.db"))))
                .scheduler(scheduler)
                .migrations(StorageQuestRepository.migrations().toArray(Migration[]::new))
                .build();
        try {
            storage.startAsync().toCompletableFuture().join();
            var repository = new StorageQuestRepository(storage);
            var playerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            var questId = QuestId.of("first-mining");
            var objectiveId = QuestObjectiveId.of("mine-diamond");
            var initial = QuestProgress.initial(playerId, questId);

            var saved = repository
                    .saveAsync(
                            new QuestProgress(
                                    playerId,
                                    questId,
                                    Map.of(objectiveId, 1L),
                                    false,
                                    Optional.empty(),
                                    Optional.empty(),
                                    Optional.empty(),
                                    initial.revision()),
                            0)
                    .toCompletableFuture()
                    .join();

            assertEquals(1, saved.revision());
            assertEquals(
                    saved,
                    repository
                            .findAsync(playerId, questId)
                            .toCompletableFuture()
                            .join()
                            .orElseThrow());

            var failure = assertThrows(
                    CompletionException.class,
                    () -> repository.saveAsync(initial, 0).toCompletableFuture().join());
            assertTrue(failure.getCause() instanceof com.cotani.quest.api.QuestProgressConflictException);
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }
}
