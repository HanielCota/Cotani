package com.cotani.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.permission.api.PermissionGroup;
import com.cotani.permission.api.PermissionOrigin;
import com.cotani.permission.api.PermissionState;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class PermissionServiceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void generatedPlayersResolvePriorityWildcardAndUserOverrides() {
        var low =
                PermissionGroup.builder("member").priority(1).allow("server.*").build();
        var high = PermissionGroup.builder("restricted")
                .priority(10)
                .deny("server.admin.*")
                .build();
        var service = CotaniPermissions.inMemory(low, high);
        try {
            StressTestSupport.scenarios("permission", "resolve-inheritance-override", (context, random, player) -> {
                StressTestSupport.await(service.assignGroupAsync(player.id(), "member"), TIMEOUT, context);
                StressTestSupport.await(service.assignGroupAsync(player.id(), "restricted"), TIMEOUT, context);

                var denied = StressTestSupport.await(
                        service.checkAsync(player.id(), "server.admin.action" + context.iteration()), TIMEOUT, context);
                assertEquals(PermissionState.DENY, denied.state(), context::description);
                assertEquals(PermissionOrigin.GROUP, denied.origin(), context::description);

                StressTestSupport.await(
                        service.allowAsync(player.id(), "server.admin.action" + context.iteration()), TIMEOUT, context);
                var overridden = StressTestSupport.await(
                        service.checkAsync(player.id(), "server.admin.action" + context.iteration()), TIMEOUT, context);
                assertTrue(overridden.allowed(), context::description);
                assertEquals(PermissionOrigin.USER, overridden.origin(), context::description);
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void oneThousandConcurrentPermissionMutationsLoseNoNodes() {
        var service = CotaniPermissions.inMemory();
        var playerId = new java.util.UUID(0x7065726dL, 1L);
        try {
            StressTestSupport.concurrent(
                    "permission",
                    "concurrent-user-overrides",
                    StressTestSupport.MINIMUM_ITERATIONS,
                    32,
                    TIMEOUT,
                    index -> service.allowAsync(playerId, "generated.node." + index));

            var decisions = StressTestSupport.concurrent(
                    "permission",
                    "concurrent-resolution",
                    StressTestSupport.MINIMUM_ITERATIONS,
                    32,
                    TIMEOUT,
                    index -> service.checkAsync(playerId, "generated.node." + index));
            assertEquals(StressTestSupport.MINIMUM_ITERATIONS, decisions.size());
            assertTrue(decisions.stream()
                    .allMatch(decision -> decision.allowed() && decision.origin() == PermissionOrigin.USER));
            assertEquals(
                    StressTestSupport.MINIMUM_ITERATIONS,
                    IntStream.range(0, StressTestSupport.MINIMUM_ITERATIONS)
                            .filter(index -> decisions.get(index).allowed())
                            .count());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}
