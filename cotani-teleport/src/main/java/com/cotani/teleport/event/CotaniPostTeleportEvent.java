package com.cotani.teleport.event;

import com.cotani.teleport.api.TeleportResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CotaniPostTeleportEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location from;
    private final Location to;
    private final TeleportResult result;

    public CotaniPostTeleportEvent(Player player, Location from, Location to, TeleportResult result) {
        this.player = player;
        this.from = from.clone();
        this.to = to.clone();
        this.result = result;
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

    public TeleportResult getResult() {
        return result;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
