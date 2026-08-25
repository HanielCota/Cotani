package com.cotani.placeholder.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a modular placeholder provider/expansion that can be registered to the {@link PlaceholderService}.
 */
@NullMarked
public interface PlaceholderExpansion {

    /**
     * Unique identifier / prefix for this expansion (e.g. "player", "coins", "clan").
     *
     * @return lowercase unique identifier
     */
    String identifier();

    /**
     * Author of this expansion.
     *
     * @return expansion author name
     */
    default String author() {
        return "Cotani";
    }

    /**
     * Version of this expansion.
     *
     * @return version string
     */
    default String version() {
        return "1.0.0";
    }

    /**
     * Whether this expansion persists across plugin/PAPI unregistrations.
     *
     * @return {@code true} if persistent
     */
    default boolean persist() {
        return true;
    }

    /**
     * Evaluates a placeholder request with a Bukkit player.
     *
     * @param player player instance, or {@code null}
     * @param params parameters after the identifier (e.g. in "%coins_balance%", params is "balance")
     * @return evaluated string result, or {@code null} if unhandled
     */
    default @Nullable String onRequest(@Nullable Player player, String params) {
        var context = player == null ? PlaceholderContext.empty() : PlaceholderContext.of(player.getUniqueId());
        return onContextRequest(context, params);
    }

    /**
     * Evaluates a placeholder request under a full {@link PlaceholderContext}.
     *
     * @param context placeholder context
     * @param params parameters after the identifier
     * @return evaluated string result, or {@code null} if unhandled
     */
    default @Nullable String onContextRequest(PlaceholderContext context, String params) {
        return null;
    }

    /**
     * Evaluates a placeholder request asynchronously and non-blockingly.
     *
     * @param context async-safe placeholder context without Bukkit object resolution
     * @param params parameters after the identifier
     * @return stage completing with evaluated string result, or completing with {@code null} if unhandled
     */
    default CompletionStage<@Nullable String> onAsyncRequest(AsyncPlaceholderContext context, String params) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "This placeholder expansion does not provide a thread-safe asynchronous handler"));
    }

    /**
     * Returns whether this expansion implements a thread-safe asynchronous handler.
     *
     * <p>Expansions that return {@code false} are evaluated on the owning Bukkit/Paper thread
     * by the service. This prevents the default synchronous implementation from touching Bukkit
     * objects on an async executor.
     *
     * @return whether {@link #onAsyncRequest(AsyncPlaceholderContext, String)} is safe to invoke async
     */
    default boolean supportsAsync() {
        return false;
    }

    /**
     * Indicates whether this expansion is relational (requiring viewer and target players).
     *
     * @return {@code true} if relational
     */
    default boolean isRelational() {
        return false;
    }

    /**
     * Evaluates a relational placeholder request comparing two players.
     *
     * @param viewer viewer player
     * @param target target player
     * @param params parameters after the identifier
     * @return evaluated string result, or {@code null} if unhandled
     */
    default @Nullable String onRelationalRequest(Player viewer, Player target, String params) {
        return null;
    }
}
