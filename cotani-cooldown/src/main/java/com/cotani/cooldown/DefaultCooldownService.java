package com.cotani.cooldown;

import java.time.Clock;
import java.util.Objects;

public final class DefaultCooldownService implements CooldownService {

    private final CooldownStore store;
    private final Clock clock;

    public DefaultCooldownService(CooldownStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public static DefaultCooldownService inMemory() {
        return new DefaultCooldownService(new InMemoryCooldownStore(), Clock.systemUTC());
    }

    public static DefaultCooldownService cacheBacked(
            com.cotani.cache.api.PlayerDataCache<com.cotani.cooldown.cache.PlayerCooldowns> playerCache) {
        return new DefaultCooldownService(
                new com.cotani.cooldown.cache.CacheCooldownStore(playerCache), Clock.systemUTC());
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
