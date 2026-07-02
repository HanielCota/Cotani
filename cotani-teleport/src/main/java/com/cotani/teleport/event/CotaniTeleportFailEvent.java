package com.cotani.teleport.event;

import com.cotani.teleport.api.TeleportResult;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CotaniTeleportFailEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TeleportResult.Failure failure;

    public CotaniTeleportFailEvent(Player player, TeleportResult.Failure failure) {
        this.player = player;
        this.failure = failure;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public TeleportResult.Failure getFailure() {
        return failure;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
