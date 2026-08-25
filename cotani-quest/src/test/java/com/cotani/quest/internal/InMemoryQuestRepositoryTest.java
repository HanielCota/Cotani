package com.cotani.quest.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestObjectiveId;
import com.cotani.quest.api.QuestProgress;
import com.cotani.quest.api.QuestProgressConflictException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class InMemoryQuestRepositoryTest {
    @Test
    void rejectsStaleRevision() {
        var repository = new InMemoryQuestRepository();
        var playerId = UUID.randomUUID();
        var questId = QuestId.of("quest");
        var progress = new QuestProgress(
                playerId,
                questId,
                Map.of(QuestObjectiveId.of("objective"), 1L),
                false,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                0);

        var saved = repository.saveAsync(progress, 0).toCompletableFuture().join();
        assertTrue(saved.revision() == 1);

        var failure = assertThrows(
                CompletionException.class,
                () -> repository.saveAsync(progress, 0).toCompletableFuture().join());
        assertTrue(failure.getCause() instanceof QuestProgressConflictException);
    }
}
