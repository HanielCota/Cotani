package com.cotani.nametag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.nametag.api.Nametag;
import com.cotani.nametag.internal.NametagRegistry;
import com.cotani.testkit.StressTestSupport;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class NametagRegistryStressTest {
    @Test
    void storesUpdatesAndRemovesThousandsOfPlayerNametagsIndependently() {
        var registry = new NametagRegistry();
        var playerIds = new ArrayList<java.util.UUID>();
        StressTestSupport.scenarios("nametag", "global-registry", (context, random, player) -> {
            var tag = Nametag.of(
                    Component.text("[rank-" + context.iteration() + "] "), Component.empty(), context.iteration());
            registry.setGlobal(player.id(), tag);
            playerIds.add(player.id());
            assertEquals(tag, registry.getGlobal(player.id()).orElseThrow(), context::description);
        });

        for (int index = 0; index < StressTestSupport.iterations(); index += 2) {
            registry.removeGlobal(playerIds.get(index));
        }
        long remaining = java.util.stream.IntStream.range(0, StressTestSupport.iterations())
                .filter(index -> registry.getGlobal(playerIds.get(index)).isPresent())
                .count();
        assertEquals(StressTestSupport.iterations() / 2L, remaining);
        registry.clear();
        assertTrue(playerIds.stream()
                .noneMatch(playerId -> registry.getGlobal(playerId).isPresent()));
    }
}
