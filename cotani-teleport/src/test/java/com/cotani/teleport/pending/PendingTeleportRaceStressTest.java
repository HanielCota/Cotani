package com.cotani.teleport.pending;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cotani.teleport.api.PendingTeleportState;
import com.cotani.teleport.api.TeleportCancelReason;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportOptions;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.bukkit.Location;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class PendingTeleportRaceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    @SuppressWarnings("NullAway")
    void cancellationRacingExecutionAlwaysPreventsCompletion() {
        int players = StressTestSupport.MINIMUM_ITERATIONS;
        var machines = IntStream.range(0, players)
                .mapToObj(index -> new PendingTeleportStateMachine(PendingTeleport.create(
                        new UUID(0x74656c65L, index + 1L),
                        new Location(null, index, 64, -index, index % 360, index % 180 - 90),
                        Duration.ofMillis(1 + index % 5_000),
                        TeleportOptions.defaults(),
                        TeleportCause.ADMIN,
                        "stress:" + index)))
                .toList();

        StressTestSupport.concurrent("teleport", "execute-vs-cancel", players * 2, 32, TIMEOUT, operation -> {
            var machine = machines.get(operation / 2);
            if ((operation & 1) == 0) {
                machine.markExecuting();
            } else {
                machine.cancel(TeleportCancelReason.QUIT);
            }
            return CompletableFuture.completedFuture(Boolean.TRUE);
        });

        machines.forEach(machine -> {
            assertEquals(PendingTeleportState.CANCELLED, machine.state());
            assertEquals(TeleportCancelReason.QUIT, machine.cancelReason().orElseThrow());
            assertFalse(machine.markCompleted(), "cancelled teleport completed after a newer session/action");
        });
    }
}
