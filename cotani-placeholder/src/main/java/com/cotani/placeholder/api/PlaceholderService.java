package com.cotani.placeholder.api;

import com.cotani.AsyncCloseable;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Service managing placeholder registration, high-speed parsing, and external bridges.
 */
@NullMarked
public interface PlaceholderService extends AutoCloseable, AsyncCloseable {

    /**
     * Registers a custom placeholder expansion.
     *
     * @param expansion expansion instance
     */
    void register(PlaceholderExpansion expansion);

    /**
     * Registers a simple synchronous placeholder handler.
     *
     * @param identifier unique placeholder identifier/prefix
     * @param handler execution handler
     */
    void register(String identifier, PlaceholderHandler handler);

    /**
     * Registers an asynchronous placeholder handler.
     *
     * @param identifier unique placeholder identifier/prefix
     * @param handler async execution handler
     */
    void registerAsync(String identifier, AsyncPlaceholderHandler handler);

    /**
     * Registers a relational placeholder handler comparing two players.
     *
     * @param identifier unique placeholder identifier/prefix
     * @param handler relational handler
     */
    void registerRelational(String identifier, RelationalPlaceholderHandler handler);

    /**
     * Unregisters an expansion by its identifier.
     *
     * @param identifier unique identifier
     * @return {@code true} if an expansion was found and removed
     */
    boolean unregister(String identifier);

    /**
     * Finds an expansion by its identifier.
     *
     * @param identifier unique identifier
     * @return optional expansion
     */
    Optional<PlaceholderExpansion> findExpansion(String identifier);

    /**
     * Returns an unmodifiable set of all registered expansion identifiers.
     *
     * @return registered identifiers
     */
    Set<String> expansions();

    /**
     * Parses placeholders in the given text with an empty context.
     *
     * @param text input string
     * @return parsed string
     */
    String parse(String text);

    /**
     * Parses placeholders in the given text for the specified player.
     *
     * @param player player instance, or {@code null}
     * @param text input string
     * @return parsed string
     */
    String parse(@Nullable Player player, String text);

    /**
     * Parses placeholders in the given text for the specified player UUID.
     *
     * @param playerUuid player UUID, or {@code null}
     * @param text input string
     * @return parsed string
     */
    String parse(@Nullable UUID playerUuid, String text);

    /**
     * Parses placeholders in the given text under a custom {@link PlaceholderContext}.
     *
     * @param context placeholder context
     * @param text input string
     * @return parsed string
     */
    String parse(PlaceholderContext context, String text);

    /**
     * Parses relational placeholders between two players.
     *
     * @param viewer viewer player
     * @param target target player
     * @param text input string
     * @return parsed string
     */
    String parseRelational(Player viewer, Player target, String text);

    /**
     * Parses relational placeholders between two player UUIDs.
     *
     * @param viewerId viewer UUID
     * @param targetId target UUID
     * @param text input string
     * @return parsed string
     */
    String parseRelational(UUID viewerId, UUID targetId, String text);

    /**
     * Parses placeholders in the given text asynchronously with an empty context.
     *
     * @param text input string
     * @return stage completing with parsed string
     */
    CompletionStage<String> parseAsync(String text);

    /**
     * Parses placeholders in the given text asynchronously for the specified player.
     *
     * <p>The player UUID is captured immediately. The overload must be called while the player
     * object is valid; asynchronous expansions receive only the immutable UUID-based context.
     *
     * @param player player instance, or {@code null}
     * @param text input string
     * @return stage completing with parsed string
     */
    CompletionStage<String> parseAsync(@Nullable Player player, String text);

    /**
     * Parses placeholders in the given text asynchronously for the specified player UUID.
     *
     * @param playerUuid player UUID, or {@code null}
     * @param text input string
     * @return stage completing with parsed string
     */
    CompletionStage<String> parseAsync(@Nullable UUID playerUuid, String text);

    /**
     * Parses placeholders in the given text asynchronously under a custom context.
     *
     * @param context placeholder context
     * @param text input string
     * @return stage completing with parsed string
     */
    CompletionStage<String> parseAsync(PlaceholderContext context, String text);

    /**
     * Parses relational placeholders asynchronously between two players.
     *
     * @param viewer viewer player
     * @param target target player
     * @param text input string
     * @return stage completing with parsed string
     */
    CompletionStage<String> parseRelationalAsync(Player viewer, Player target, String text);

    /**
     * Parses relational placeholders asynchronously between two player UUIDs.
     *
     * @param viewerId viewer UUID
     * @param targetId target UUID
     * @param text input string
     * @return stage completing with parsed string
     */
    CompletionStage<String> parseRelationalAsync(UUID viewerId, UUID targetId, String text);

    /**
     * Parses placeholders and returns an Adventure {@link Component} parsed via MiniMessage.
     *
     * @param text raw MiniMessage text with placeholders
     * @return formatted adventure component
     */
    Component parseComponent(String text);

    /**
     * Parses placeholders for a player and returns an Adventure {@link Component}.
     *
     * @param player player instance, or {@code null}
     * @param text raw MiniMessage text with placeholders
     * @return formatted adventure component
     */
    Component parseComponent(@Nullable Player player, String text);

    /**
     * Parses placeholders under a context and returns an Adventure {@link Component}.
     *
     * @param context placeholder context
     * @param text raw MiniMessage text with placeholders
     * @return formatted adventure component
     */
    Component parseComponent(PlaceholderContext context, String text);

    /**
     * Creates an Adventure {@link TagResolver} dynamically resolving placeholders in MiniMessage.
     *
     * @param context placeholder context
     * @return dynamic tag resolver
     */
    TagResolver tagResolver(PlaceholderContext context);

    @Override
    void close();
}
