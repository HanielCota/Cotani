package com.cotani.cooldown.cache;

import com.cotani.cooldown.api.CooldownEntry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PlayerCooldowns(UUID playerId, Map<String, CooldownEntry> activeCooldowns) {
    public PlayerCooldowns(UUID playerId) {
        this(playerId, new ConcurrentHashMap<>());
    }
}
