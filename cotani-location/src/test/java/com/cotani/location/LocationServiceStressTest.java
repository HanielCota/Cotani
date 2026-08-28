package com.cotani.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.location.api.LocationName;
import com.cotani.location.api.LocationPosition;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class LocationServiceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void generatedPlayersRoundTripHomesAcrossWorldsAndCoordinateBoundaries() {
        var service = CotaniLocations.inMemory();
        try {
            StressTestSupport.scenarios("location", "home-round-trip", (context, random, player) -> {
                var name = LocationName.of("home-" + context.iteration());
                var position = new LocationPosition(
                        random.uuid("world"),
                        random.nextLong(-30_000_000L, 30_000_001L),
                        random.nextInt(-2_048, 2_049),
                        random.nextLong(-30_000_000L, 30_000_001L),
                        random.nextInt(-180, 181),
                        random.nextInt(-90, 91));

                var saved =
                        StressTestSupport.await(service.setHomeAsync(player.id(), name, position), TIMEOUT, context);
                var loaded = StressTestSupport.await(service.findHomeAsync(player.id(), name), TIMEOUT, context)
                        .orElseThrow();
                assertEquals(saved, loaded, context::description);
                assertEquals(position, loaded.position(), context::description);
                StressTestSupport.await(service.deleteHomeAsync(player.id(), name), TIMEOUT, context);
                assertTrue(
                        StressTestSupport.await(service.findHomeAsync(player.id(), name), TIMEOUT, context)
                                .isEmpty(),
                        context::description);
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void oneThousandPlayersCanPersistIndependentHomesConcurrently() {
        var service = CotaniLocations.inMemory();
        var worldId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
        try {
            var homes = StressTestSupport.concurrent(
                    "location",
                    "concurrent-home-save",
                    StressTestSupport.MINIMUM_ITERATIONS,
                    32,
                    TIMEOUT,
                    index -> service.setHomeAsync(
                            new UUID(0x6c6f6361L, index + 1L),
                            LocationName.of("base"),
                            new LocationPosition(worldId, index, 64, -index, 0, 0)));
            assertEquals(StressTestSupport.MINIMUM_ITERATIONS, homes.size());
            assertEquals(
                    StressTestSupport.MINIMUM_ITERATIONS,
                    homes.stream().map(home -> home.id().ownerId()).distinct().count());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}
