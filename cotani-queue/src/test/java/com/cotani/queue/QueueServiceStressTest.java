package com.cotani.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.queue.api.QueueEntryOptions;
import com.cotani.queue.api.QueueId;
import com.cotani.queue.api.QueueMatch;
import com.cotani.queue.api.QueueTicket;
import com.cotani.testkit.StressTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class QueueServiceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void generatedEnqueueDequeueJourneysNeverLeaveGhostTickets() {
        var service = CotaniQueues.inMemory();
        try {
            StressTestSupport.scenarios("queue", "enqueue-dequeue", (context, random, player) -> {
                var queueId = QueueId.of("generated-" + context.iteration());
                var options = new QueueEntryOptions(
                        random.nextInt(-100, 101), random.duration(Duration.ofSeconds(1), Duration.ofHours(1)));
                var ticket =
                        StressTestSupport.await(service.enqueueAsync(queueId, player.id(), options), TIMEOUT, context);
                assertEquals(
                        Optional.of(ticket),
                        StressTestSupport.await(service.findByPlayerAsync(player.id()), TIMEOUT, context),
                        context::description);
                StressTestSupport.await(service.dequeueAsync(ticket.ticketId()), TIMEOUT, context);
                assertTrue(
                        StressTestSupport.await(service.findByPlayerAsync(player.id()), TIMEOUT, context)
                                .isEmpty(),
                        context::description);
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void oneThousandConcurrentEntriesAreMatchedExactlyOnce() {
        var service = CotaniQueues.inMemory();
        var queueId = QueueId.of("massive");
        int players = StressTestSupport.MINIMUM_ITERATIONS;
        var playerIds = IntStream.range(0, players)
                .mapToObj(index -> UUID.nameUUIDFromBytes(("queue:" + index).getBytes(StandardCharsets.UTF_8)))
                .toList();
        try {
            StressTestSupport.concurrent(
                    "queue",
                    "simultaneous-enqueue",
                    players,
                    32,
                    TIMEOUT,
                    index -> service.enqueueAsync(
                            queueId,
                            playerIds.get(index),
                            QueueEntryOptions.defaults().withPriority(index % 17)));
            assertEquals(
                    players,
                    service.entriesAsync(queueId).toCompletableFuture().join().size());

            var matches = StressTestSupport.concurrent(
                    "queue", "competing-match", players / 10, 16, TIMEOUT, index -> service.matchAsync(queueId, 10));
            var matchedPlayers = new HashSet<UUID>();
            for (Optional<QueueMatch> match : matches) {
                assertTrue(match.isPresent());
                for (QueueTicket ticket : match.orElseThrow().tickets()) {
                    assertTrue(
                            matchedPlayers.add(ticket.playerId()),
                            "player matched more than once: " + ticket.playerId());
                }
            }
            assertEquals(players, matchedPlayers.size());
            assertTrue(
                    service.entriesAsync(queueId).toCompletableFuture().join().isEmpty());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}
