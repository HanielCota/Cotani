package com.cotani.cooldown.cache;

import com.cotani.cooldown.api.CooldownEntry;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PlayerCooldowns(UUID playerId, Map<String, CooldownEntry> activeCooldowns) {
    public PlayerCooldowns {
        Objects.requireNonNull(activeCooldowns, "activeCooldowns");
    }

    public PlayerCooldowns(UUID playerId) {
        this(playerId, new ConcurrentHashMap<>());
    }

    @Override
    public Map<String, CooldownEntry> activeCooldowns() {
        return Map.copyOf(activeCooldowns);
    }

    public Map<String, CooldownEntry> activeCooldownsUnsafe() {
        return activeCooldowns;
    }

    public Optional<CooldownEntry> find(String action) {
        return Optional.ofNullable(activeCooldowns.get(action));
    }

    public void put(CooldownEntry entry) {
        Objects.requireNonNull(entry, "entry");
        activeCooldowns.put(entry.key().action().value(), entry);
    }

    public void remove(String action) {
        activeCooldowns.remove(action);
    }
}
