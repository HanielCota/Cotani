package com.cotani.cooldown.paper;

import com.cotani.cooldown.api.CooldownKey;
import java.time.Duration;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CotaniCooldownStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final CooldownKey key;
    private final Duration duration;

    public CotaniCooldownStartEvent(CooldownKey key, Duration duration) {
        this.key = Objects.requireNonNull(key, "key");
        this.duration = Objects.requireNonNull(duration, "duration");
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public CooldownKey getKey() {
        return key;
    }

    public Duration getDuration() {
        return duration;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
