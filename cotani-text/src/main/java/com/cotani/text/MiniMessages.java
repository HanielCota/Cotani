package com.cotani.text;

import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NullMarked;

/**
 * Utilities for parsing and serializing MiniMessage strings.
 *
 * <p>MiniMessage is the preferred way to represent rich text in configuration files and user
 * input. This class wraps the default Adventure {@link MiniMessage}
 * instance with short, null-safe methods that integrate cleanly with {@link Placeholders}.
 */
@NullMarked
public final class MiniMessages {

    private static final String INPUT_NULL_MESSAGE = "Parameter 'input' must not be null";
    private static final String TARGET_NULL_MESSAGE = "Parameter 'target' must not be null";
    private static final String COMPONENT_NULL_MESSAGE = "Parameter 'component' must not be null";

    private MiniMessages() {
        throw new AssertionError("utility class");
    }

    /**
     * Parses a MiniMessage string into a component using the default tag set.
     *
     * @param input the MiniMessage string
     * @return the parsed component
     */
    public static Component parse(String input) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);

        return ComponentSerializers.MINIMESSAGE.deserialize(input);
    }

    /**
     * Parses a MiniMessage string into a component, replacing the given placeholders.
     *
     * @param input the MiniMessage string
     * @param resolvers the tag resolvers to apply
     * @return the parsed component
     */
    public static Component parse(String input, TagResolver... resolvers) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);

        return ComponentSerializers.MINIMESSAGE.deserialize(input, resolvers);
    }

    /**
     * Parses a MiniMessage string into a component for a specific target, applying the given
     * placeholders.
     *
     * @param input the MiniMessage string
     * @param target the target of the deserialization
     * @param resolvers the tag resolvers to apply
     * @return the parsed component
     */
    public static Component parse(String input, Audience target, TagResolver... resolvers) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);
        Objects.requireNonNull(target, TARGET_NULL_MESSAGE);

        return ComponentSerializers.MINIMESSAGE.deserialize(input, target, resolvers);
    }

    /**
     * Serializes a component into a MiniMessage string.
     *
     * @param component the component to serialize
     * @return the MiniMessage representation
     */
    public static String serialize(Component component) {
        Objects.requireNonNull(component, COMPONENT_NULL_MESSAGE);

        return ComponentSerializers.MINIMESSAGE.serialize(component);
    }

    /**
     * Escapes all known MiniMessage tags in the input so they are treated as literal text.
     *
     * @param input the input string
     * @return the escaped string
     */
    public static String escape(String input) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);

        return ComponentSerializers.MINIMESSAGE.escapeTags(input);
    }

    /**
     * Escapes all known MiniMessage tags, including those provided by custom resolvers.
     *
     * @param input the input string
     * @param resolvers the custom tag resolvers
     * @return the escaped string
     */
    public static String escape(String input, TagResolver... resolvers) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);

        return ComponentSerializers.MINIMESSAGE.escapeTags(input, resolvers);
    }

    /**
     * Removes all supported MiniMessage tags from the input.
     *
     * @param input the input string
     * @return the string without tags
     */
    public static String strip(String input) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);

        return ComponentSerializers.MINIMESSAGE.stripTags(input);
    }

    /**
     * Removes all MiniMessage tags from the input, including those provided by custom resolvers.
     *
     * @param input the input string
     * @param resolvers the custom tag resolvers
     * @return the string without tags
     */
    public static String strip(String input, TagResolver... resolvers) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);

        return ComponentSerializers.MINIMESSAGE.stripTags(input, resolvers);
    }
}
