package com.cotani.placeholder.impl;

import com.cotani.api.InternalApi;
import com.cotani.placeholder.api.AsyncPlaceholderContext;
import com.cotani.placeholder.api.AsyncPlaceholderHandler;
import com.cotani.placeholder.api.PlaceholderContext;
import com.cotani.placeholder.api.PlaceholderExpansion;
import com.cotani.placeholder.api.PlaceholderHandler;
import com.cotani.placeholder.api.RelationalPlaceholderHandler;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Functional wrapper allowing lambda registrations as {@link PlaceholderExpansion}.
 */
@InternalApi
@NullMarked
public final class FunctionalExpansion implements PlaceholderExpansion {

    private final String identifier;
    private final @Nullable PlaceholderHandler syncHandler;
    private final @Nullable AsyncPlaceholderHandler asyncHandler;
    private final @Nullable RelationalPlaceholderHandler relationalHandler;

    private FunctionalExpansion(
            String identifier,
            @Nullable PlaceholderHandler syncHandler,
            @Nullable AsyncPlaceholderHandler asyncHandler,
            @Nullable RelationalPlaceholderHandler relationalHandler) {
        this.identifier = Objects.requireNonNull(identifier, "Parameter 'identifier' must not be null")
                .toLowerCase(java.util.Locale.ROOT);
        this.syncHandler = syncHandler;
        this.asyncHandler = asyncHandler;
        this.relationalHandler = relationalHandler;
    }

    public static FunctionalExpansion ofSync(String identifier, PlaceholderHandler handler) {
        Objects.requireNonNull(handler, "Parameter 'handler' must not be null");
        return new FunctionalExpansion(identifier, handler, null, null);
    }

    public static FunctionalExpansion ofAsync(String identifier, AsyncPlaceholderHandler handler) {
        Objects.requireNonNull(handler, "Parameter 'handler' must not be null");
        return new FunctionalExpansion(identifier, null, handler, null);
    }

    public static FunctionalExpansion ofRelational(String identifier, RelationalPlaceholderHandler handler) {
        Objects.requireNonNull(handler, "Parameter 'handler' must not be null");
        return new FunctionalExpansion(identifier, null, null, handler);
    }

    @Override
    public String identifier() {
        return identifier;
    }

    @Override
    public @Nullable String onContextRequest(PlaceholderContext context, String params) {
        if (syncHandler != null) {
            return syncHandler.handle(context, params);
        }
        return null;
    }

    @Override
    public CompletionStage<@Nullable String> onAsyncRequest(AsyncPlaceholderContext context, String params) {
        if (asyncHandler != null) {
            return asyncHandler.handleAsync(context, params);
        }
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This functional expansion only provides a synchronous handler"));
    }

    @Override
    public boolean supportsAsync() {
        return asyncHandler != null;
    }

    @Override
    public boolean isRelational() {
        return relationalHandler != null;
    }

    @Override
    public @Nullable String onRelationalRequest(Player viewer, Player target, String params) {
        if (relationalHandler != null) {
            return relationalHandler.handleRelational(viewer, target, params);
        }
        return null;
    }
}
