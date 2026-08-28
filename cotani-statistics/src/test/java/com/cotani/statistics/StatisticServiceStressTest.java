package com.cotani.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.cotani.event.api.EventBus;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticOperationId;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class StatisticServiceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final StatisticId ACTIONS = StatisticId.of("generated-actions");

    @Test
    void generatedPlayerUpdatesAreIdempotentAndKeepExactValues() {
        var service = CotaniStatistics.inMemory(eventBus());
        try {
            StressTestSupport.scenarios("statistics", "idempotent-increment", (context, random, player) -> {
                long amount = random.nextLong(1, 1_000_001);
                var operationId = StatisticOperationId.of(random.uuid("operation"));
                var first = StressTestSupport.await(
                        service.incrementAsync(player.id(), ACTIONS, amount, operationId), TIMEOUT, context);
                var replay = StressTestSupport.await(
                        service.incrementAsync(player.id(), ACTIONS, amount, operationId), TIMEOUT, context);

                assertEquals(first, replay, context::description);
                assertEquals(amount, replay.value(), context::description);
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void oneThousandSimultaneousIncrementsOnOnePlayerLoseNoUpdates() {
        var service = CotaniStatistics.inMemory(eventBus());
        var playerId = UUID.fromString("12121212-1212-1212-1212-121212121212");
        int operations = StressTestSupport.MINIMUM_ITERATIONS;
        try {
            StressTestSupport.concurrent(
                    "statistics",
                    "same-player-increment",
                    operations,
                    32,
                    TIMEOUT,
                    index -> service.incrementAsync(
                            playerId,
                            ACTIONS,
                            1,
                            StatisticOperationId.of(UUID.nameUUIDFromBytes(
                                    ("stat:" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)))));

            var result = service.findAsync(playerId, ACTIONS)
                    .toCompletableFuture()
                    .join()
                    .orElseThrow();
            assertEquals(operations, result.value());
            assertEquals(operations, result.revision());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    private static EventBus eventBus() {
        var eventBus = mock(EventBus.class);
        doAnswer(invocation -> java.util.concurrent.CompletableFuture.completedFuture(invocation.getArgument(0)))
                .when(eventBus)
                .publishAsync(any());
        return eventBus;
    }
}
