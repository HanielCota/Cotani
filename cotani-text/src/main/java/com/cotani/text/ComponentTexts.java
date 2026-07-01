package com.cotani.text;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jspecify.annotations.NullMarked;

/**
 * Factory and conversion utilities for common {@link Component} operations.
 *
 * <p>This class centralizes the creation of simple components and conversions between the most
 * common text representations used in Minecraft plugins: MiniMessage, legacy ({@code &}), legacy
 * section ({@code §}), plain text, and JSON.
 */
@NullMarked
public final class ComponentTexts {

    private static final String COMPONENT_NULL_MESSAGE = "Parameter 'component' must not be null";
    private static final String CONTENT_NULL_MESSAGE = "Parameter 'content' must not be null";
    private static final String LEGACY_NULL_MESSAGE = "Parameter 'legacy' must not be null";
    private static final String MINI_MESSAGE_NULL_MESSAGE = "Parameter 'miniMessage' must not be null";
    private static final String PLAIN_NULL_MESSAGE = "Parameter 'plain' must not be null";
    private static final String SEPARATOR_NULL_MESSAGE = "Parameter 'separator' must not be null";
    private static final String PARTS_NULL_MESSAGE = "Parameter 'parts' must not be null";
    private static final String DECORATION_NULL_MESSAGE = "Parameter 'decoration' must not be null";
    private static final String JSON_NULL_MESSAGE = "Parameter 'json' must not be null";

    private ComponentTexts() {
        throw new AssertionError("utility class");
    }

    /**
     * Gets an empty component.
     *
     * @return an empty component
     */
    public static Component empty() {
        return Component.empty();
    }

    /**
     * Creates a text component with the given content.
     *
     * @param content the text content
     * @return a text component
     */
    public static Component text(String content) {
        Objects.requireNonNull(content, CONTENT_NULL_MESSAGE);

        return Component.text(content);
    }

    /**
     * Creates a text component with the given content and color.
     *
     * @param content the text content
     * @param color the text color
     * @return a text component
     */
    public static Component text(String content, TextColor color) {
        Objects.requireNonNull(content, CONTENT_NULL_MESSAGE);

        return Component.text(content, color);
    }

    /**
     * Gets a component representing a newline.
     *
     * @return a newline component
     */
    public static Component newline() {
        return Component.newline();
    }

    /**
     * Gets a component representing a single space.
     *
     * @return a space component
     */
    public static Component space() {
        return Component.space();
    }

    /**
     * Joins multiple components with a separator.
     *
     * @param separator the separator to place between components
     * @param parts the components to join
     * @return the joined component
     */
    public static Component join(ComponentLike separator, ComponentLike... parts) {
        validateJoin(separator, parts);

        return Component.join(JoinConfiguration.separator(separator), parts);
    }

    /**
     * Joins multiple components with a separator.
     *
     * @param separator the separator to place between components
     * @param parts the components to join
     * @return the joined component
     */
    public static Component join(ComponentLike separator, Iterable<? extends ComponentLike> parts) {
        Objects.requireNonNull(separator, SEPARATOR_NULL_MESSAGE);
        Objects.requireNonNull(parts, PARTS_NULL_MESSAGE);

        return Component.join(JoinConfiguration.separator(separator), parts);
    }

    /**
     * Creates a component from a plain text string.
     *
     * @param plain the plain text
     * @return the component
     */
    public static Component fromPlain(String plain) {
        Objects.requireNonNull(plain, PLAIN_NULL_MESSAGE);

        return ComponentSerializers.PLAIN.deserialize(plain);
    }

    /**
     * Creates a component from a legacy ampersand ({@code &}) formatted string.
     *
     * @param legacy the legacy text
     * @return the component
     */
    public static Component fromLegacy(String legacy) {
        Objects.requireNonNull(legacy, LEGACY_NULL_MESSAGE);

        return ComponentSerializers.LEGACY_AMPERSAND.deserialize(legacy);
    }

    /**
     * Creates a component from a legacy section ({@code §}) formatted string.
     *
     * @param legacy the legacy text
     * @return the component
     */
    public static Component fromLegacySection(String legacy) {
        Objects.requireNonNull(legacy, LEGACY_NULL_MESSAGE);

        return ComponentSerializers.LEGACY_SECTION.deserialize(legacy);
    }

    /**
     * Creates a component from a MiniMessage string.
     *
     * @param miniMessage the MiniMessage string
     * @return the component
     */
    public static Component fromMiniMessage(String miniMessage) {
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_NULL_MESSAGE);

        return MiniMessages.parse(miniMessage);
    }

    /**
     * Serializes a component to plain text.
     *
     * @param component the component
     * @return the plain text representation
     */
    public static String toPlain(Component component) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        return ComponentSerializers.PLAIN.serialize(component);
    }

    /**
     * Serializes a component to a legacy ampersand ({@code &}) formatted string.
     *
     * @param component the component
     * @return the legacy representation
     */
    public static String toLegacy(Component component) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        return ComponentSerializers.LEGACY_AMPERSAND.serialize(component);
    }

    /**
     * Serializes a component to a legacy section ({@code §}) formatted string.
     *
     * @param component the component
     * @return the legacy representation
     */
    public static String toLegacySection(Component component) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        return ComponentSerializers.LEGACY_SECTION.serialize(component);
    }

    /**
     * Serializes a component to a MiniMessage string.
     *
     * @param component the component
     * @return the MiniMessage representation
     */
    public static String toMiniMessage(Component component) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        return MiniMessages.serialize(component);
    }

    /**
     * Serializes a component to a JSON string.
     *
     * @param component the component
     * @return the JSON representation
     */
    public static String toJson(Component component) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        return ComponentSerializers.GSON.serialize(component);
    }

    /**
     * Deserializes a component from a JSON string.
     *
     * @param json the JSON string
     * @return the deserialized component
     */
    public static Component fromJson(String json) {
        Objects.requireNonNull(json, JSON_NULL_MESSAGE);

        return ComponentSerializers.GSON.deserialize(json);
    }

    /**
     * Returns a copy of the component with italic decoration explicitly disabled.
     *
     * <p>Useful for item lore, which inherits an italic style from the rendering parent.
     *
     * @param component the component
     * @return the component without italics
     */
    public static Component withoutItalics(Component component) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        return component.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Returns a copy of the component with the given decoration explicitly disabled.
     *
     * @param component the component
     * @param decoration the decoration to disable
     * @return the component without the decoration
     */
    public static Component withoutDecoration(Component component, TextDecoration decoration) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);
        Objects.requireNonNull(decoration, DECORATION_NULL_MESSAGE);

        return component.decoration(decoration, false);
    }

    /**
     * Returns a compact copy of the component with redundant style elements and children removed.
     *
     * @param component the component
     * @return the compact component
     */
    public static Component compact(Component component) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        return component.compact();
    }

    private static void validateJoin(ComponentLike separator, ComponentLike[] parts) {
        Objects.requireNonNull(separator, SEPARATOR_NULL_MESSAGE);
        Objects.requireNonNull(parts, PARTS_NULL_MESSAGE);
    }
}
