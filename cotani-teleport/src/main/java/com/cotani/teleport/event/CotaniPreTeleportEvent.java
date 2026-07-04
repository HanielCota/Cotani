package com.cotani.teleport.event;

import com.cotani.teleport.api.TeleportCause;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CotaniPreTeleportEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location from;
    private final TeleportCause cause;
    private final String source;
    private Location to;
    private boolean cancelled;

    public CotaniPreTeleportEvent(Player player, Location from, Location to, TeleportCause cause, String source) {
        this.player = Objects.requireNonNull(player, "player");
        this.from = Objects.requireNonNull(from, "from").clone();
        this.to = Objects.requireNonNull(to, "to").clone();
        this.cause = Objects.requireNonNull(cause, "cause");
        this.source = Objects.requireNonNull(source, "source");
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getFrom() {
        return from.clone();
    }

    public Location getTo() {
        return to.clone();
    }

    public void setTo(Location to) {
        this.to = Objects.requireNonNull(to, "to").clone();
    }

    public TeleportCause getCause() {
        return cause;
    }

    public String getSource() {
        return source;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
