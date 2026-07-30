package com.cotani.text;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MiniMessages {

    private static final String INPUT_NULL_MESSAGE = "Parameter 'input' must not be null";
    private static final String INPUTS_NULL_MESSAGE = "Parameter 'inputs' must not be null";
    private static final String TARGET_NULL_MESSAGE = "Parameter 'target' must not be null";
    private static final String COMPONENT_NULL_MESSAGE = "Parameter 'component' must not be null";
    private static final int PARSE_CACHE_MAX_SIZE = 512;
    private static final int MAX_TEMPLATE_LENGTH = 32_768;
    private static final Map<String, Component> parseCache =
            Collections.synchronizedMap(new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
                    return size() > PARSE_CACHE_MAX_SIZE;
                }
            });

    private MiniMessages() {}

    /**
     * Parses a trusted MiniMessage template.
     *
     * <p>Do not pass player-controlled text to this method: supported tags can create interactive
     * components. Use {@link #literal(String)} for untrusted text.
     */
    public static Component parse(String input) {
        requireBoundedTemplate(input);

        if (input.indexOf('<') < 0) {
            return Component.text(input);
        }
        return parseCache.computeIfAbsent(input, ComponentSerializers.MINIMESSAGE::deserialize);
    }

    /**
     * Parses a MiniMessage string into a component, replacing the given placeholders.
     *
     * @param input the MiniMessage string
     * @param resolvers the tag resolvers to apply
     * @return the parsed component
     */
    public static Component parse(String input, TagResolver... resolvers) {
        requireBoundedTemplate(input);

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
        requireBoundedTemplate(input);
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

    /** Creates a literal component from untrusted text without interpreting MiniMessage tags. */
    public static Component literal(String input) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);
        return Component.text(input);
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

    /**
     * Parses a collection of MiniMessage strings into a list of components using the default tag set.
     *
     * @param inputs the collection of MiniMessage strings
     * @return the list of parsed components
     */
    public static List<Component> parseList(Collection<String> inputs) {
        Objects.requireNonNull(inputs, INPUTS_NULL_MESSAGE);

        return inputs.stream().map(MiniMessages::parse).toList();
    }

    /**
     * Parses a collection of MiniMessage strings into a list of components, replacing the given placeholders.
     *
     * @param inputs the collection of MiniMessage strings
     * @param resolvers the tag resolvers to apply
     * @return the list of parsed components
     */
    public static List<Component> parseList(Collection<String> inputs, TagResolver... resolvers) {
        Objects.requireNonNull(inputs, INPUTS_NULL_MESSAGE);
        Objects.requireNonNull(resolvers, "Parameter 'resolvers' must not be null");

        return inputs.stream().map(input -> parse(input, resolvers)).toList();
    }

    /**
     * Parses a collection of MiniMessage strings into a list of components for a specific target,
     * applying the given placeholders.
     *
     * @param inputs the collection of MiniMessage strings
     * @param target the target of the deserialization
     * @param resolvers the tag resolvers to apply
     * @return the list of parsed components
     */
    public static List<Component> parseList(Collection<String> inputs, Audience target, TagResolver... resolvers) {
        Objects.requireNonNull(inputs, INPUTS_NULL_MESSAGE);
        Objects.requireNonNull(target, TARGET_NULL_MESSAGE);
        Objects.requireNonNull(resolvers, "Parameter 'resolvers' must not be null");

        return inputs.stream().map(input -> parse(input, target, resolvers)).toList();
    }

    /**
     * Serializes a collection of components into a list of MiniMessage strings.
     *
     * @param components the collection of components to serialize
     * @return the list of MiniMessage representations
     */
    public static List<String> serializeList(Collection<? extends Component> components) {
        Objects.requireNonNull(components, "Parameter 'components' must not be null");

        return components.stream().map(MiniMessages::serialize).toList();
    }

    private static void requireBoundedTemplate(String input) {
        Objects.requireNonNull(input, INPUT_NULL_MESSAGE);
        if (input.length() > MAX_TEMPLATE_LENGTH) {
            throw new IllegalArgumentException("MiniMessage template exceeds maximum length " + MAX_TEMPLATE_LENGTH);
        }
    }
}
