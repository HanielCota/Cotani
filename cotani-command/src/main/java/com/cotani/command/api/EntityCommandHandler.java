package com.cotani.command.api;

import org.bukkit.entity.Player;

/**
 * Functional handler for command execution scheduled on the player's entity/region thread (Paper and Folia).
 */
@FunctionalInterface
public interface EntityCommandHandler {
    /**
     * Executes the command logic on the player's entity/region thread.
     *
     * @param context the command execution context
     * @param player the player executing the command
     * @throws Exception if an unhandled error occurs
     */
    void executeEntity(CommandContext context, Player player) throws Exception;
}
