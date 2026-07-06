package com.cotani.cooldown;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cooldown.api.CooldownService;
import com.cotani.cooldown.cache.PlayerCooldowns;
import com.cotani.cooldown.internal.DefaultCooldownService;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class CotaniCooldowns {

    private CotaniCooldowns() {}

    public static CooldownService inMemory() {
        return DefaultCooldownService.inMemory();
    }

    public static CooldownService inMemory(java.time.Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new DefaultCooldownService(new com.cotani.cooldown.internal.InMemoryCooldownStore(), clock);
    }

    public static CooldownService cacheBacked(PlayerDataCache<PlayerCooldowns> playerCache) {
        Objects.requireNonNull(playerCache, "playerCache");
        return DefaultCooldownService.cacheBacked(playerCache);
    }
}
