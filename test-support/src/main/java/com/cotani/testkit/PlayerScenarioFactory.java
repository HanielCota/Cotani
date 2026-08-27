package com.cotani.testkit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Builds varied, deterministic player fixtures from a scenario seed. */
public final class PlayerScenarioFactory {
    private PlayerScenarioFactory() {}

    public static TestPlayer player(SeededRandom random, int index) {
        var playerId = random.uuid("player-" + index);
        var sessionId = random.uuid("session-" + index);
        var permissions = random.nextBoolean() ? Set.of("cotani.user", "cotani.command.use") : Set.<String>of();
        var inventory = random.nextBoolean() ? List.of("minecraft:stone", "minecraft:diamond") : List.<String>of();
        var locale = random.nextBoolean() ? Locale.US : Locale.forLanguageTag("pt-BR");
        var location = new TestPlayer.TestLocation(
                random.uuid("world"),
                random.nextInt(-30_000_000, 30_000_001),
                random.nextInt(-64, 321),
                random.nextInt(-30_000_000, 30_000_001));
        return new TestPlayer(
                playerId,
                "Player" + index,
                sessionId,
                random.nextBoolean(),
                permissions,
                BigDecimal.valueOf(random.nextLong(0, 1_000_001), 2),
                inventory,
                locale,
                location);
    }
}
