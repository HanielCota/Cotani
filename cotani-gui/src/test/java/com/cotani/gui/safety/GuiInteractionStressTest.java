package com.cotani.gui.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.gui.state.State;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class GuiInteractionStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void oneThousandPlayersAreDebouncedIndependentlyAndReleasedOnQuit() {
        var debouncer = new ClickDebouncer(Duration.ofMinutes(1));
        var acquisitions = StressTestSupport.concurrent(
                "gui",
                "player-click",
                StressTestSupport.MINIMUM_ITERATIONS,
                32,
                TIMEOUT,
                index -> java.util.concurrent.CompletableFuture.completedFuture(
                        debouncer.tryAcquire(new UUID(0x677569L, index + 1L))));
        assertTrue(acquisitions.stream().allMatch(Boolean::booleanValue));

        for (int index = 0; index < StressTestSupport.MINIMUM_ITERATIONS; index++) {
            var playerId = new UUID(0x677569L, index + 1L);
            assertTrue(!debouncer.tryAcquire(playerId), "spam click escaped debounce for " + playerId);
            debouncer.remove(playerId);
            assertTrue(debouncer.tryAcquire(playerId), "quit/reconnect retained stale debounce for " + playerId);
        }
        debouncer.clear();
    }

    @Test
    void reactiveGuiStateLosesNoConcurrentUpdatesAndClosesSubscriptions() {
        var state = State.of(0);
        var notifications = new AtomicInteger();
        var subscription = state.observe(_ -> notifications.incrementAndGet());

        StressTestSupport.concurrent(
                "gui", "reactive-update", StressTestSupport.MINIMUM_ITERATIONS, 32, TIMEOUT, index -> {
                    state.update(value -> value + 1);
                    return java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE);
                });
        assertEquals(StressTestSupport.MINIMUM_ITERATIONS, state.get());
        assertEquals(StressTestSupport.MINIMUM_ITERATIONS, notifications.get());

        subscription.close();
        state.set(-1);
        assertEquals(StressTestSupport.MINIMUM_ITERATIONS, notifications.get());
    }
}
