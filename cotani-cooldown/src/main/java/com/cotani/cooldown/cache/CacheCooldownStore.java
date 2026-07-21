package com.cotani.cooldown.cache;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownResult;
import com.cotani.cooldown.api.CooldownStore;
import com.cotani.cooldown.api.UserCooldownTarget;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CacheCooldownStore implements CooldownStore {

    private static final Logger LOGGER = Logger.getLogger(CacheCooldownStore.class.getName());

    private final PlayerDataCache<PlayerCooldowns> playerCache;
    private final ConcurrentMap<CooldownKey, CooldownEntry> nonPlayerEntries = new ConcurrentHashMap<>();

    public CacheCooldownStore(PlayerDataCache<PlayerCooldowns> playerCache) {
        this.playerCache = Objects.requireNonNull(playerCache, "playerCache");
    }

    @Override
    public Optional<CooldownEntry> find(CooldownKey key) {
        Objects.requireNonNull(key, "key");

        if (key.target() instanceof UserCooldownTarget(UUID userId)) {
            return playerCache
                    .find(userId)
                    .map(pc -> pc.activeCooldowns().get(key.action().value()));
        }

        return Optional.ofNullable(nonPlayerEntries.get(key));
    }

    @Override
    public void save(CooldownEntry entry) {
        Objects.requireNonNull(entry, "entry");

        CooldownKey key = entry.key();
        if (key.target() instanceof UserCooldownTarget(UUID userId)) {
            Optional<PlayerCooldowns> optional = playerCache.find(userId);
            if (optional.isPresent()) {
                PlayerCooldowns playerCooldowns = optional.get();
                playerCooldowns.activeCooldowns().put(key.action().value(), entry);
                playerCache.markDirty(userId);
                playerCache.mutateAsync(
                        userId, pc -> pc.activeCooldowns().put(key.action().value(), entry));
            } else {
                LOGGER.log(
                        Level.WARNING,
                        () -> "Cooldown for user " + userId
                                + " was not persisted because the player cache is not loaded: " + key);
            }
            return;
        }

        nonPlayerEntries.put(key, entry);
    }

    @Override
    public void remove(CooldownKey key) {
        Objects.requireNonNull(key, "key");

        if (key.target() instanceof UserCooldownTarget(UUID userId)) {
            Optional<PlayerCooldowns> optional = playerCache.find(userId);
            if (optional.isPresent()) {
                PlayerCooldowns playerCooldowns = optional.get();
                playerCooldowns.activeCooldowns().remove(key.action().value());
                playerCache.markDirty(userId);
                playerCache.mutateAsync(
                        userId, pc -> pc.activeCooldowns().remove(key.action().value()));
            }
            return;
        }

        nonPlayerEntries.remove(key);
    }

    @Override
    public void removeExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        nonPlayerEntries.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    @Override
    public void clear() {
        nonPlayerEntries.clear();
    }

    @Override
    public CooldownResult checkAndStart(CooldownKey key, Duration duration, Clock clock) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(clock, "clock");

        if (key.target() instanceof UserCooldownTarget(UUID userId)) {
            Optional<PlayerCooldowns> optional = playerCache.find(userId);
            if (optional.isEmpty()) {
                // The player cache is not loaded. We must NOT fall through into nonPlayerEntries:
                // find() never reads user cooldowns from that map, so the cooldown would be
                // silently lost. User cooldowns require the player data to be loaded first
                // (e.g. via PlayerDataCache.getOrLoadAsync). Failing loudly keeps the bug observable.
                throw new IllegalStateException("Cannot check/start cooldown for user " + userId
                        + " because the player cache is not loaded. Load the player first: " + key);
            }

            PlayerCooldowns playerCooldowns = optional.get();
            Instant now = clock.instant();
            CooldownEntry current =
                    playerCooldowns.activeCooldowns().get(key.action().value());

            if (current != null && !current.expired(now)) {
                return CooldownResult.denied(key, current.remaining(now), current.expiresAt());
            }

            Instant expiresAt = now.plus(duration);
            CooldownEntry created = new CooldownEntry(key, now, expiresAt);
            playerCooldowns.activeCooldowns().put(key.action().value(), created);
            playerCache.markDirty(userId);
            playerCache.mutateAsync(
                    userId, pc -> pc.activeCooldowns().put(key.action().value(), created));

            return CooldownResult.allowed(key);
        }

        Instant now = clock.instant();
        AtomicReference<CooldownResult> resultReference = new AtomicReference<>();

        nonPlayerEntries.compute(key, (ignored, current) -> {
            if (current != null && !current.expired(now)) {
                resultReference.set(CooldownResult.denied(key, current.remaining(now), current.expiresAt()));

                return current;
            }

            Instant expiresAt = now.plus(duration);
            CooldownEntry created = new CooldownEntry(key, now, expiresAt);
            resultReference.set(CooldownResult.allowed(key));

            return created;
        });

        return Objects.requireNonNull(resultReference.get());
    }
}
