package com.cotani.cooldown.cache;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cooldown.api.*;
import com.cotani.cooldown.paper.CotaniCooldownDenyEvent;
import com.cotani.cooldown.paper.CotaniCooldownStartEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CacheCooldownStore implements CooldownStore {

    private final PlayerDataCache<PlayerCooldowns> playerCache;
    private final ConcurrentMap<CooldownKey, CooldownEntry> nonPlayerEntries = new ConcurrentHashMap<>();

    public CacheCooldownStore(PlayerDataCache<PlayerCooldowns> playerCache) {
        this.playerCache = Objects.requireNonNull(playerCache, "playerCache");
    }

    @Override
    public Optional<CooldownEntry> find(CooldownKey key) {
        Objects.requireNonNull(key, "key");

        if (key.target() instanceof UserCooldownTarget(UUID userId)) {
            Optional<PlayerCooldowns> optional = playerCache.find(userId);
            return optional.map(playerCooldowns ->
                    playerCooldowns.activeCooldowns().get(key.action().value()));
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
                optional.get().activeCooldowns().put(key.action().value(), entry);
                playerCache.markDirty(userId);
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
                if (optional.get().activeCooldowns().remove(key.action().value()) != null) {
                    playerCache.markDirty(userId);
                }
            }
            return;
        }

        nonPlayerEntries.remove(key);
    }

    @Override
    public void removeExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        nonPlayerEntries.entrySet().removeIf(entry -> entry.getValue().expired(clock));
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

        CooldownResult result;
        if (key.target() instanceof UserCooldownTarget(UUID userId)) {
            PlayerCooldowns playerCooldowns = playerCache.get(userId);
            Instant now = clock.instant();
            CooldownEntry current =
                    playerCooldowns.activeCooldowns().get(key.action().value());

            if (current != null && !current.expired(clock)) {
                result = CooldownResult.denied(key, current.remaining(clock), current.expiresAt());
            } else {
                Instant expiresAt = now.plus(duration);
                CooldownEntry created = new CooldownEntry(key, now, expiresAt);
                playerCooldowns.activeCooldowns().put(key.action().value(), created);
                playerCache.markDirty(userId);
                result = CooldownResult.allowed(key);
            }
        } else {
            Instant now = clock.instant();
            AtomicReference<CooldownResult> resultReference = new AtomicReference<>();

            nonPlayerEntries.compute(key, (ignored, current) -> {
                if (current != null && !current.expired(clock)) {
                    resultReference.set(CooldownResult.denied(key, current.remaining(clock), current.expiresAt()));

                    return current;
                }

                Instant expiresAt = now.plus(duration);
                CooldownEntry created = new CooldownEntry(key, now, expiresAt);
                resultReference.set(CooldownResult.allowed(key));

                return created;
            });

            result = Objects.requireNonNull(resultReference.get());
        }

        // Fire Bukkit Events safely if on the primary server thread
        if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
            if (result.denied()) {
                Bukkit.getPluginManager()
                        .callEvent(new CotaniCooldownDenyEvent(
                                key, result.remaining(), Objects.requireNonNull(result.expiresAt())));
            } else {
                Bukkit.getPluginManager().callEvent(new CotaniCooldownStartEvent(key, duration));
            }
        }

        return result;
    }
}
