package com.cotani.friend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class FriendServiceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void variedPlayerJourneysPreserveBlockAndFriendshipInvariants() {
        var service = CotaniFriends.inMemory();
        try {
            StressTestSupport.scenarios("friend", "request-accept-block", (context, random, player) -> {
                var targetId = random.uuid("target");
                var request =
                        StressTestSupport.await(service.sendRequestAsync(player.id(), targetId), TIMEOUT, context);
                var friendship = StressTestSupport.await(
                        service.acceptRequestAsync(targetId, request.requesterId()), TIMEOUT, context);

                assertTrue(friendship.contains(player.id()), context::description);
                assertTrue(
                        StressTestSupport.await(service.areFriendsAsync(player.id(), targetId), TIMEOUT, context),
                        context::description);

                if (context.iteration() % 3 == 0) {
                    StressTestSupport.await(service.blockAsync(targetId, player.id()), TIMEOUT, context);
                    assertFalse(
                            StressTestSupport.await(service.areFriendsAsync(player.id(), targetId), TIMEOUT, context),
                            context::description);
                    assertEquals(
                            List.of(player.id()),
                            StressTestSupport.await(service.blocksAsync(targetId), TIMEOUT, context).stream()
                                    .map(block -> block.blockedId())
                                    .toList(),
                            context::description);
                }
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void simultaneousRequestsAndAcceptsAgainstOnePlayerLoseNoRelationships() {
        var service = CotaniFriends.inMemory();
        int players = StressTestSupport.MINIMUM_ITERATIONS;
        var hub = java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var requesters = IntStream.range(0, players)
                .mapToObj(index -> java.util.UUID.nameUUIDFromBytes(
                        ("friend:" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .toList();
        try {
            StressTestSupport.concurrent(
                    "friend",
                    "simultaneous-request",
                    players,
                    32,
                    TIMEOUT,
                    index -> service.sendRequestAsync(requesters.get(index), hub));
            assertEquals(
                    players,
                    service.incomingRequestsAsync(hub)
                            .toCompletableFuture()
                            .join()
                            .size());

            StressTestSupport.concurrent(
                    "friend",
                    "simultaneous-accept",
                    players,
                    32,
                    TIMEOUT,
                    index -> service.acceptRequestAsync(hub, requesters.get(index)));

            assertEquals(
                    players,
                    service.friendsAsync(hub).toCompletableFuture().join().size());
            assertTrue(service.incomingRequestsAsync(hub)
                    .toCompletableFuture()
                    .join()
                    .isEmpty());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}
