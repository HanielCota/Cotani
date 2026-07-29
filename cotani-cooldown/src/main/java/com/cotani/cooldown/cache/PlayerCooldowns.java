package com.cotani.cooldown.cache;

import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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

    public CooldownResult checkAndStart(com.cotani.cooldown.api.CooldownKey key, Duration duration, Instant now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(now, "now");
        var result = new AtomicReference<CooldownResult>();
        activeCooldowns.compute(key.action().value(), (_, current) -> {
            if (current != null && !current.expired(now)) {
                result.set(CooldownResult.denied(key, current.remaining(now), current.expiresAt()));
                return current;
            }
            var created = new CooldownEntry(key, now, now.plus(duration));
            result.set(CooldownResult.allowed(key));
            return created;
        });
        return Objects.requireNonNull(result.get(), "cooldown result");
    }
}
