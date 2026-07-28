package com.cotani.cooldown.cache;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cooldown.api.*;
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
import org.jspecify.annotations.Nullable;

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
            return playerCache.find(userId).flatMap(pc -> pc.find(key.action().value()));
        }

        return Optional.ofNullable(nonPlayerEntries.get(key));
    }

    @Override
    public void save(CooldownEntry entry) {
        Objects.requireNonNull(entry, "entry");
        CooldownKey key = entry.key();
        if (key.target() instanceof UserCooldownTarget(UUID userId)) {
            Optional<PlayerCooldowns> optional = playerCache.find(userId);
            if (optional.isEmpty()) {
                LOGGER.log(
                        Level.WARNING,
                        () -> "Cooldown for user " + userId
                                + " was not persisted because the player cache is not loaded: " + key);
                return;
            }

            PlayerCooldowns playerCooldowns = optional.get();
            playerCooldowns.put(entry);
            playerCache.markDirty(userId);
            playerCache.mutateAsync(userId, pc -> pc.put(entry));
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
                playerCooldowns.remove(key.action().value());
                playerCache.markDirty(userId);
                playerCache.mutateAsync(userId, pc -> pc.remove(key.action().value()));
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
            return checkAndStartUserCooldown(userId, key, duration, clock);
        }

        return checkAndStartNonPlayerCooldown(key, duration, clock);
    }

    private CooldownResult checkAndStartUserCooldown(UUID userId, CooldownKey key, Duration duration, Clock clock) {
        Optional<PlayerCooldowns> optional = playerCache.find(userId);
        if (optional.isEmpty()) {
            throw new IllegalStateException("Cannot check/start cooldown for user " + userId
                    + " because the player cache is not loaded. Load the player first: " + key);
        }

        PlayerCooldowns playerCooldowns = optional.get();
        Instant now = clock.instant();
        var current = playerCooldowns.find(key.action().value());

        if (current.isPresent() && !current.get().expired(now)) {
            var entry = current.get();
            return CooldownResult.denied(key, entry.remaining(now), entry.expiresAt());
        }

        Instant expiresAt = now.plus(duration);
        CooldownEntry created = new CooldownEntry(key, now, expiresAt);
        playerCooldowns.put(created);
        playerCache.markDirty(userId);
        playerCache.mutateAsync(userId, pc -> pc.put(created));

        return CooldownResult.allowed(key);
    }

    private CooldownResult checkAndStartNonPlayerCooldown(CooldownKey key, Duration duration, Clock clock) {
        Instant now = clock.instant();
        AtomicReference<@Nullable CooldownResult> resultReference = new AtomicReference<>();

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
