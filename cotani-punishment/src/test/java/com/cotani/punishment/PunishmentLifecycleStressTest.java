package com.cotani.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.audit.api.AuditActor;
import com.cotani.punishment.api.Punishment;
import com.cotani.punishment.api.PunishmentId;
import com.cotani.punishment.api.PunishmentQuery;
import com.cotani.punishment.api.PunishmentRequest;
import com.cotani.punishment.api.PunishmentStatus;
import com.cotani.punishment.api.PunishmentType;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class PunishmentLifecycleStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void generatedModerationLifecyclesRemainIdempotentAndPlayerIsolated() {
        var service = CotaniPunishments.inMemory();
        try {
            StressTestSupport.scenarios("punishment", "apply-query-revoke", (context, random, player) -> {
                var punishmentId = new PunishmentId(random.uuid("punishment"));
                var type =
                        switch (context.iteration() % 3) {
                            case 0 -> PunishmentType.BAN;
                            case 1 -> PunishmentType.MUTE;
                            default -> PunishmentType.WARN;
                        };
                var request = new PunishmentRequest(
                        punishmentId,
                        player.id(),
                        AuditActor.system(),
                        type,
                        "generated moderation " + context.iteration(),
                        NOW,
                        Optional.of(NOW.plusSeconds(random.nextLong(1, 86_401))));

                var applied = StressTestSupport.await(service.applyAsync(request), TIMEOUT, context);
                var replay = StressTestSupport.await(service.applyAsync(request), TIMEOUT, context);
                assertEquals(applied, replay, context::description);

                var active = StressTestSupport.await(
                        service.queryAsync(PunishmentQuery.builder()
                                .targetId(player.id())
                                .activeAt(NOW.plusMillis(1))
                                .build()),
                        TIMEOUT,
                        context);
                assertEquals(1, active.size(), context::description);
                assertEquals(punishmentId, active.getFirst().id(), context::description);

                if (context.iteration() % 3 == 0) {
                    var revoked = StressTestSupport.await(
                                    service.revokeAsync(
                                            punishmentId,
                                            new Punishment.Revocation(
                                                    AuditActor.system(), "generated appeal", NOW.plusSeconds(1))),
                                    TIMEOUT,
                                    context)
                            .orElseThrow();
                    assertEquals(PunishmentStatus.REVOKED, revoked.statusAt(NOW.plusSeconds(1)), context::description);
                    assertTrue(
                            StressTestSupport.await(
                                            service.queryAsync(PunishmentQuery.builder()
                                                    .targetId(player.id())
                                                    .activeAt(NOW.plusSeconds(2))
                                                    .build()),
                                            TIMEOUT,
                                            context)
                                    .isEmpty(),
                            context::description);
                }
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}
