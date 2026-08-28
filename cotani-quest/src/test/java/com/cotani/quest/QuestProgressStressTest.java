package com.cotani.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.cotani.event.api.EventBus;
import com.cotani.quest.api.QuestClaimId;
import com.cotani.quest.api.QuestDefinition;
import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestObjective;
import com.cotani.quest.api.QuestObjectiveId;
import com.cotani.reward.api.RewardId;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class QuestProgressStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final QuestId QUEST_ID = QuestId.of("generated-mining");
    private static final QuestObjectiveId OBJECTIVE_ID = QuestObjectiveId.of("mine-stone");

    @Test
    void generatedPlayersProgressCompleteAndClaimExactlyOnce() {
        var eventBus = mock(EventBus.class);
        doAnswer(invocation -> java.util.concurrent.CompletableFuture.completedFuture(invocation.getArgument(0)))
                .when(eventBus)
                .publishAsync(any());
        var service = CotaniQuests.inMemory(eventBus);
        service.register(new QuestDefinition(
                QUEST_ID,
                List.of(new QuestObjective(OBJECTIVE_ID, "mine", "stone", 10)),
                RewardId.of("generated-reward")));
        try {
            StressTestSupport.scenarios("quest", "progress-complete-claim", (context, random, player) -> {
                long firstAmount = random.nextLong(1, 10);
                var partial = StressTestSupport.await(
                        service.recordProgressAsync(player.id(), QUEST_ID, OBJECTIVE_ID, firstAmount),
                        TIMEOUT,
                        context);
                assertEquals(firstAmount, partial.progressFor(OBJECTIVE_ID), context::description);

                var completed = StressTestSupport.await(
                        service.recordProgressAsync(player.id(), QUEST_ID, OBJECTIVE_ID, 10), TIMEOUT, context);
                assertTrue(completed.completed(), context::description);
                assertEquals(10, completed.progressFor(OBJECTIVE_ID), context::description);

                var claimId = new QuestClaimId(random.uuid("claim"));
                var first =
                        StressTestSupport.await(service.claimAsync(player.id(), QUEST_ID, claimId), TIMEOUT, context);
                var replay =
                        StressTestSupport.await(service.claimAsync(player.id(), QUEST_ID, claimId), TIMEOUT, context);
                assertEquals(first, replay, context::description);
                assertEquals(claimId.value(), replay.rewardClaimId().value(), context::description);
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}
