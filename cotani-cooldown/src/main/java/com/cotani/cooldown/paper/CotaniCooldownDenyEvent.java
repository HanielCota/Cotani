package com.cotani.cooldown.paper;

import com.cotani.cooldown.api.CooldownKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CotaniCooldownDenyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final CooldownKey key;
    private final Duration remaining;
    private final Instant expiresAt;

    public CotaniCooldownDenyEvent(CooldownKey key, Duration remaining, Instant expiresAt) {
        this.key = Objects.requireNonNull(key, "key");
        this.remaining = Objects.requireNonNull(remaining, "remaining");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public CooldownKey getKey() {
        return key;
    }

    public Duration getRemaining() {
        return remaining;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
