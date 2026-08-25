package com.cotani.placeholder.api;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Functional handler for relational placeholder evaluation comparing two players.
 */
@FunctionalInterface
@NullMarked
public interface RelationalPlaceholderHandler {

    /**
     * Handles and evaluates a relational placeholder between two players.
     *
     * @param viewer viewer player
     * @param target target player
     * @param params arguments/suffix passed to the placeholder
     * @return evaluated string result, or {@code null} if unhandled
     */
    @Nullable
    String handleRelational(Player viewer, Player target, String params);
}
