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
import java.util.concurrent.atomic.AtomicLong;
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
    private final AtomicLong nextCleanupEpochMilli = new AtomicLong(Long.MIN_VALUE);

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

            playerCache.mutateAsync(userId, pc -> pc.put(entry)).whenComplete((_, failure) -> {
                if (failure != null) {
                    LOGGER.log(Level.SEVERE, "Failed to save cooldown for user " + userId, failure);
                }
            });
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
                playerCache
                        .mutateAsync(userId, pc -> pc.remove(key.action().value()))
                        .whenComplete((_, failure) -> {
                            if (failure != null) {
                                LOGGER.log(Level.SEVERE, "Failed to remove cooldown for user " + userId, failure);
                            }
                        });
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

        if (!duration.isPositive()) {
            throw new IllegalArgumentException("duration must be positive");
        }

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
        var result = playerCooldowns.checkAndStart(key, duration, now);

        if (result.allowed()) {
            playerCache.markDirty(userId);
        }
        return result;
    }

    private CooldownResult checkAndStartNonPlayerCooldown(CooldownKey key, Duration duration, Clock clock) {
        Instant now = clock.instant();
        cleanupNonPlayerEntriesWhenDue(now);
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

    private void cleanupNonPlayerEntriesWhenDue(Instant now) {
        long nowMillis = now.toEpochMilli();
        long nextCleanup = nextCleanupEpochMilli.get();

        if (nowMillis < nextCleanup || !nextCleanupEpochMilli.compareAndSet(nextCleanup, safeNextCleanup(now))) {
            return;
        }
        nonPlayerEntries.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    private static long safeNextCleanup(Instant now) {
        try {
            return now.plus(Duration.ofMinutes(1)).toEpochMilli();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
