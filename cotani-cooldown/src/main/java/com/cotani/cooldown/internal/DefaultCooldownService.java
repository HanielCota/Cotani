package com.cotani.cooldown.internal;

import com.cotani.api.InternalApi;
import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cooldown.api.*;
import com.cotani.cooldown.cache.CacheCooldownStore;
import com.cotani.cooldown.cache.PlayerCooldowns;
import java.time.Clock;
import java.util.Objects;

@InternalApi
public final class DefaultCooldownService implements CooldownService {

    private final CooldownStore store;
    private final Clock clock;

    public DefaultCooldownService(CooldownStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public static DefaultCooldownService inMemory() {
        return new DefaultCooldownService(new InMemoryCooldownStore(), new MonotonicClock());
    }

    public static DefaultCooldownService cacheBacked(PlayerDataCache<PlayerCooldowns> playerCache) {
        return new DefaultCooldownService(new CacheCooldownStore(playerCache), Clock.systemUTC());
    }

    @Override
    public CooldownOperation target(CooldownTarget target) {
        Objects.requireNonNull(target, "target cannot be null");

        return new DefaultCooldownOperation(store, clock, target);
    }

    @Override
    public void remove(CooldownTarget target, CooldownAction action) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        store.remove(new CooldownKey(target, action));
    }

    @Override
    public void clearExpired() {
        store.removeExpired(clock);
    }

    @Override
    public void clearAll() {
        store.clear();
    }
}
