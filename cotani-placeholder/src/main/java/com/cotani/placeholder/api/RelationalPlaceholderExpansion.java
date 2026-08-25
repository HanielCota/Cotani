package com.cotani.placeholder.api;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Specialized expansion contract for relational placeholders.
 */
@NullMarked
public interface RelationalPlaceholderExpansion extends PlaceholderExpansion {

    @Override
    default boolean isRelational() {
        return true;
    }

    @Override
    @Nullable
    String onRelationalRequest(Player viewer, Player target, String params);
}
