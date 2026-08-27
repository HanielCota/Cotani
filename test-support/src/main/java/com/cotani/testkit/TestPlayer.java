package com.cotani.testkit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable player state used without retaining live Bukkit objects. */
public record TestPlayer(
        UUID id,
        String username,
        UUID sessionId,
        boolean online,
        Set<String> permissions,
        BigDecimal balance,
        List<String> inventory,
        Locale locale,
        TestLocation location) {
    public TestPlayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(sessionId, "sessionId");
        permissions = Set.copyOf(permissions);
        Objects.requireNonNull(balance, "balance");
        inventory = List.copyOf(inventory);
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(location, "location");
    }

    public TestPlayer reconnect(UUID newSessionId) {
        return new TestPlayer(id, username, newSessionId, true, permissions, balance, inventory, locale, location);
    }

    public TestPlayer disconnect() {
        return new TestPlayer(id, username, sessionId, false, permissions, balance, inventory, locale, location);
    }

    public record TestLocation(UUID worldId, double x, double y, double z) {
        public TestLocation {
            Objects.requireNonNull(worldId, "worldId");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("coordinates must be finite");
            }
        }
    }
}
