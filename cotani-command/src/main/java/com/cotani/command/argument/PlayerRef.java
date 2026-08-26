package com.cotani.command.argument;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Immutable snapshot of an online player captured while parsing a command on the main thread.
 *
 * <p>Instances are safe to hold and read across async boundaries: handlers work with the player's
 * identity ({@link #id()}) and display name instead of retaining live Bukkit objects. Re-resolve
 * the live player through {@code PaperTaskScheduler} entity transitions when mutation is required.
 */
public record PlayerRef(UUID id, String name) {
    public PlayerRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

    /**
     * Captures a reference from a live player.
     *
     * <p>Must be called from the thread that owns the player (main or entity thread), which is
     * always the case for argument parsers executed during dispatch.
     *
     * @param player online player
     * @return an immutable reference to the player
     */
    public static PlayerRef of(Player player) {
        Objects.requireNonNull(player, "player");
        return new PlayerRef(player.getUniqueId(), player.getName());
    }
}
