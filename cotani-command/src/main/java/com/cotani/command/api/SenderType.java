package com.cotani.command.api;

import java.util.Objects;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * Defines which type of sender is allowed to execute a command.
 */
public enum SenderType {
    /**
     * The command can be executed by any sender (players, console, block, etc.).
     */
    ANY {
        @Override
        public boolean test(CommandSender sender) {
            Objects.requireNonNull(sender, "sender");
            return true;
        }
    },

    /**
     * The command can only be executed by an in-game {@link Player}.
     */
    PLAYER {
        @Override
        public boolean test(CommandSender sender) {
            Objects.requireNonNull(sender, "sender");
            return sender instanceof Player;
        }
    },

    /**
     * The command can only be executed by the server console or remote console.
     */
    CONSOLE {
        @Override
        public boolean test(CommandSender sender) {
            Objects.requireNonNull(sender, "sender");
            return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
        }
    };

    /**
     * Tests whether the given sender satisfies this sender requirement.
     *
     * @param sender the command sender to check
     * @return {@code true} if allowed, {@code false} otherwise
     */
    public abstract boolean test(CommandSender sender);
}
