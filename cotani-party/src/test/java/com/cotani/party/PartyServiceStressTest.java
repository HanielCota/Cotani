package com.cotani.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.party.api.PartyOptions;
import com.cotani.testkit.StressTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class PartyServiceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void generatedPartyJourneysAlwaysKeepTheLeaderInsideTheParty() {
        var service = CotaniParties.inMemory();
        try {
            StressTestSupport.scenarios("party", "create-invite-transfer", (context, random, leader) -> {
                var memberId = random.uuid("member");
                var party = StressTestSupport.await(
                        service.createAsync(leader.id(), new PartyOptions(2 + context.iteration() % 7)),
                        TIMEOUT,
                        context);
                StressTestSupport.await(
                        service.inviteAsync(party.id(), leader.id(), memberId, Duration.ofMinutes(1)),
                        TIMEOUT,
                        context);
                var joined = StressTestSupport.await(service.acceptInviteAsync(memberId, party.id()), TIMEOUT, context);
                var transferred = StressTestSupport.await(
                        service.transferLeadershipAsync(joined.id(), leader.id(), memberId), TIMEOUT, context);

                assertEquals(memberId, transferred.leaderId(), context::description);
                assertTrue(transferred.contains(transferred.leaderId()), context::description);
                assertEquals(2, transferred.members().size(), context::description);
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void concurrentInvitationsAndJoinsRespectCapacityWithoutLostMembers() {
        var service = CotaniParties.inMemory();
        int members = StressTestSupport.MINIMUM_ITERATIONS;
        var leader = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        var playerIds = IntStream.range(0, members)
                .mapToObj(index -> UUID.nameUUIDFromBytes(("party:" + index).getBytes(StandardCharsets.UTF_8)))
                .toList();
        try {
            var party = service.createAsync(leader, new PartyOptions(members + 1))
                    .toCompletableFuture()
                    .join();
            StressTestSupport.concurrent(
                    "party",
                    "simultaneous-invite",
                    members,
                    32,
                    TIMEOUT,
                    index -> service.inviteAsync(party.id(), leader, playerIds.get(index), Duration.ofMinutes(5)));
            StressTestSupport.concurrent(
                    "party",
                    "simultaneous-accept",
                    members,
                    32,
                    TIMEOUT,
                    index -> service.acceptInviteAsync(playerIds.get(index), party.id()));

            var result =
                    service.findAsync(party.id()).toCompletableFuture().join().orElseThrow();
            assertEquals(members + 1, result.members().size());
            assertTrue(result.contains(result.leaderId()));
            assertTrue(service.invitesAsync(playerIds.get(0))
                    .toCompletableFuture()
                    .join()
                    .isEmpty());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}
