package com.cotani.text;

import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NullMarked;

/**
 * Utilities for sending MiniMessage strings to {@link Audience}s.
 *
 * <p>These methods convert MiniMessage input (with optional placeholders) into components and
 * forward them to the appropriate Adventure audience method. They eliminate the repetitive
 * {@code deserialize(...).sendMessage(...)} boilerplate found in most plugins.
 */
@NullMarked
public final class AudienceMessages {

    private static final String AUDIENCE_NULL_MESSAGE = "Parameter 'audience' must not be null";
    private static final String MINI_MESSAGE_NULL_MESSAGE = "Parameter 'miniMessage' must not be null";
    private static final String RESOLVERS_NULL_MESSAGE = "Parameter 'resolvers' must not be null";

    private AudienceMessages() {
        throw new AssertionError("utility class");
    }

    /**
     * Sends a MiniMessage chat message to the audience.
     *
     * @param audience the audience to receive the message
     * @param miniMessage the MiniMessage string
     * @param resolvers optional placeholders to apply
     */
    public static void sendMessage(Audience audience, String miniMessage, TagResolver... resolvers) {
        validate(audience, miniMessage, resolvers);
        audience.sendMessage(MiniMessages.parse(miniMessage, resolvers));
    }

    /**
     * Sends a MiniMessage action bar message to the audience.
     *
     * @param audience the audience to receive the message
     * @param miniMessage the MiniMessage string
     * @param resolvers optional placeholders to apply
     */
    public static void sendActionBar(Audience audience, String miniMessage, TagResolver... resolvers) {
        validate(audience, miniMessage, resolvers);
        audience.sendActionBar(MiniMessages.parse(miniMessage, resolvers));
    }

    /**
     * Sends a MiniMessage player list header to the audience.
     *
     * @param audience the audience to receive the header
     * @param miniMessage the MiniMessage string
     * @param resolvers optional placeholders to apply
     */
    public static void sendPlayerListHeader(Audience audience, String miniMessage, TagResolver... resolvers) {
        validate(audience, miniMessage, resolvers);
        audience.sendPlayerListHeader(MiniMessages.parse(miniMessage, resolvers));
    }

    /**
     * Sends a MiniMessage player list footer to the audience.
     *
     * @param audience the audience to receive the footer
     * @param miniMessage the MiniMessage string
     * @param resolvers optional placeholders to apply
     */
    public static void sendPlayerListFooter(Audience audience, String miniMessage, TagResolver... resolvers) {
        validate(audience, miniMessage, resolvers);
        audience.sendPlayerListFooter(MiniMessages.parse(miniMessage, resolvers));
    }

    /**
     * Sends both the player list header and footer to the audience.
     *
     * @param audience the audience to receive the header and footer
     * @param header the header MiniMessage string
     * @param footer the footer MiniMessage string
     * @param resolvers optional placeholders to apply to both texts
     */
    public static void sendPlayerListHeaderAndFooter(
            Audience audience, String header, String footer, TagResolver... resolvers) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(header, "Parameter 'header' must not be null");
        Objects.requireNonNull(footer, "Parameter 'footer' must not be null");
        Objects.requireNonNull(resolvers, RESOLVERS_NULL_MESSAGE);

        audience.sendPlayerListHeaderAndFooter(
                MiniMessages.parse(header, resolvers), MiniMessages.parse(footer, resolvers));
    }

    private static void validate(Audience audience, String miniMessage, TagResolver[] resolvers) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_NULL_MESSAGE);
        Objects.requireNonNull(resolvers, RESOLVERS_NULL_MESSAGE);
    }
}
