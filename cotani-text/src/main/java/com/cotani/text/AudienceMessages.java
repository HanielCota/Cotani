package com.cotani.text;

import java.time.Duration;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.jspecify.annotations.NullMarked;

/**
 * Utilities for sending MiniMessage strings and {@link Component}s to {@link Audience}s.
 *
 * <p>These methods convert MiniMessage input (with optional placeholders) or components and
 * forward them to the appropriate Adventure audience method. They eliminate the repetitive
 * boilerplate found in most plugins.
 */
@NullMarked
public final class AudienceMessages {

    private static final String AUDIENCE_NULL_MESSAGE = "Parameter 'audience' must not be null";
    private static final String COMPONENT_NULL_MESSAGE = "Parameter 'component' must not be null";
    private static final String MINI_MESSAGE_NULL_MESSAGE = "Parameter 'miniMessage' must not be null";
    private static final String RESOLVERS_NULL_MESSAGE = "Parameter 'resolvers' must not be null";
    private static final String HEADER_NULL_MESSAGE = "Parameter 'header' must not be null";
    private static final String FOOTER_NULL_MESSAGE = "Parameter 'footer' must not be null";
    private static final String TITLE_NULL_MESSAGE = "Parameter 'title' must not be null";
    private static final String SUBTITLE_NULL_MESSAGE = "Parameter 'subtitle' must not be null";
    private static final String FADE_IN_NULL_MESSAGE = "Parameter 'fadeIn' must not be null";
    private static final String STAY_NULL_MESSAGE = "Parameter 'stay' must not be null";
    private static final String FADE_OUT_NULL_MESSAGE = "Parameter 'fadeOut' must not be null";

    private AudienceMessages() {}

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
     * Sends a Component chat message to the audience.
     *
     * @param audience the audience to receive the message
     * @param component the component to send
     */
    public static void sendMessage(Audience audience, Component component) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        audience.sendMessage(component);
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
     * Sends a Component action bar message to the audience.
     *
     * @param audience the audience to receive the message
     * @param component the component to send
     */
    public static void sendActionBar(Audience audience, Component component) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        audience.sendActionBar(component);
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
     * Sends a Component player list header to the audience.
     *
     * @param audience the audience to receive the header
     * @param component the component to send
     */
    public static void sendPlayerListHeader(Audience audience, Component component) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        audience.sendPlayerListHeader(component);
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
     * Sends a Component player list footer to the audience.
     *
     * @param audience the audience to receive the footer
     * @param component the component to send
     */
    public static void sendPlayerListFooter(Audience audience, Component component) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        audience.sendPlayerListFooter(component);
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
        Objects.requireNonNull(header, HEADER_NULL_MESSAGE);
        Objects.requireNonNull(footer, FOOTER_NULL_MESSAGE);
        Objects.requireNonNull(resolvers, RESOLVERS_NULL_MESSAGE);

        audience.sendPlayerListHeaderAndFooter(
                MiniMessages.parse(header, resolvers), MiniMessages.parse(footer, resolvers));
    }

    /**
     * Sends both the player list header and footer components to the audience.
     *
     * @param audience the audience to receive the header and footer
     * @param header the header component
     * @param footer the footer component
     */
    public static void sendPlayerListHeaderAndFooter(Audience audience, Component header, Component footer) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(header, HEADER_NULL_MESSAGE);
        Objects.requireNonNull(footer, FOOTER_NULL_MESSAGE);

        audience.sendPlayerListHeaderAndFooter(header, footer);
    }

    /**
     * Sends a MiniMessage title and subtitle to the audience.
     *
     * @param audience the audience to receive the title
     * @param titleMiniMessage the title MiniMessage string
     * @param subtitleMiniMessage the subtitle MiniMessage string
     * @param resolvers optional placeholders to apply
     */
    public static void sendTitle(
            Audience audience, String titleMiniMessage, String subtitleMiniMessage, TagResolver... resolvers) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(titleMiniMessage, TITLE_NULL_MESSAGE);
        Objects.requireNonNull(subtitleMiniMessage, SUBTITLE_NULL_MESSAGE);
        Objects.requireNonNull(resolvers, RESOLVERS_NULL_MESSAGE);

        audience.showTitle(Title.title(
                MiniMessages.parse(titleMiniMessage, resolvers), MiniMessages.parse(subtitleMiniMessage, resolvers)));
    }

    /**
     * Sends a Component title and subtitle to the audience.
     *
     * @param audience the audience to receive the title
     * @param title the title component
     * @param subtitle the subtitle component
     */
    public static void sendTitle(Audience audience, Component title, Component subtitle) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(title, TITLE_NULL_MESSAGE);
        Objects.requireNonNull(subtitle, SUBTITLE_NULL_MESSAGE);

        audience.showTitle(Title.title(title, subtitle));
    }

    /**
     * Sends a MiniMessage title and subtitle to the audience with custom times.
     *
     * @param audience the audience to receive the title
     * @param titleMiniMessage the title MiniMessage string
     * @param subtitleMiniMessage the subtitle MiniMessage string
     * @param fadeIn the fade in duration
     * @param stay the stay duration
     * @param fadeOut the fade out duration
     * @param resolvers optional placeholders to apply
     */
    public static void sendTitle(
            Audience audience,
            String titleMiniMessage,
            String subtitleMiniMessage,
            Duration fadeIn,
            Duration stay,
            Duration fadeOut,
            TagResolver... resolvers) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(titleMiniMessage, TITLE_NULL_MESSAGE);
        Objects.requireNonNull(subtitleMiniMessage, SUBTITLE_NULL_MESSAGE);
        Objects.requireNonNull(fadeIn, FADE_IN_NULL_MESSAGE);
        Objects.requireNonNull(stay, STAY_NULL_MESSAGE);
        Objects.requireNonNull(fadeOut, FADE_OUT_NULL_MESSAGE);
        Objects.requireNonNull(resolvers, RESOLVERS_NULL_MESSAGE);

        audience.showTitle(Title.title(
                MiniMessages.parse(titleMiniMessage, resolvers),
                MiniMessages.parse(subtitleMiniMessage, resolvers),
                Title.Times.times(fadeIn, stay, fadeOut)));
    }

    /**
     * Sends a Component title and subtitle to the audience with custom times.
     *
     * @param audience the audience to receive the title
     * @param title the title component
     * @param subtitle the subtitle component
     * @param fadeIn the fade in duration
     * @param stay the stay duration
     * @param fadeOut the fade out duration
     */
    public static void sendTitle(
            Audience audience, Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(title, TITLE_NULL_MESSAGE);
        Objects.requireNonNull(subtitle, SUBTITLE_NULL_MESSAGE);
        Objects.requireNonNull(fadeIn, FADE_IN_NULL_MESSAGE);
        Objects.requireNonNull(stay, STAY_NULL_MESSAGE);
        Objects.requireNonNull(fadeOut, FADE_OUT_NULL_MESSAGE);

        audience.showTitle(Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut)));
    }

    private static void validate(Audience audience, String miniMessage, TagResolver[] resolvers) {
        Objects.requireNonNull(audience, AUDIENCE_NULL_MESSAGE);
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_NULL_MESSAGE);
        Objects.requireNonNull(resolvers, RESOLVERS_NULL_MESSAGE);
    }
}
