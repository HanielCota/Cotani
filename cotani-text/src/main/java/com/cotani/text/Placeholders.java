package com.cotani.text;

import java.text.ChoiceFormat;
import java.time.temporal.TemporalAccessor;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.StyleBuilderApplicable;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NullMarked;

/**
 * Fluent factory for MiniMessage {@link TagResolver}s used as dynamic placeholders.
 *
 * <p>This class provides short, descriptive methods over the Adventure
 * {@link Placeholder} and {@link Formatter} APIs, reducing boilerplate when replacing tags such as
 * {@code <player>}, {@code <balance>}, or {@code <date>}.
 *
 * <p>Formatting placeholders ({@code number}, {@code date}, {@code choice}) expect their format
 * arguments to be supplied inside the MiniMessage tag itself (for example
 * {@code <no:'#.00'>} or {@code <date:'yyyy-MM-dd'>}). This mirrors the upstream Adventure API.
 */
@SuppressWarnings("UnsubstitutedExpression")
@NullMarked
public final class Placeholders {

    private static final String KEY_NULL_MESSAGE = "Parameter 'key' must not be null";
    private static final String VALUE_NULL_MESSAGE = "Parameter 'value' must not be null";
    private static final String STYLE_NULL_MESSAGE = "Parameter 'style' must not be null";
    private static final String COMPONENTS_NULL_MESSAGE = "Parameter 'components' must not be null";
    private static final String RESOLVERS_NULL_MESSAGE = "Parameter 'resolvers' must not be null";

    private Placeholders() {}

    /**
     * Creates a placeholder that inserts the given component directly.
     *
     * @param key the tag name
     * @param value the component to insert
     * @return a tag resolver for the placeholder
     */
    public static TagResolver component(String key, Component value) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(value, VALUE_NULL_MESSAGE);

        return Placeholder.component(key, value);
    }

    /**
     * Creates a placeholder that inserts raw text without parsing MiniMessage tags inside it.
     *
     * @param key the tag name
     * @param value the raw text to insert
     * @return a tag resolver for the placeholder
     */
    public static TagResolver unparsed(String key, String value) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(value, VALUE_NULL_MESSAGE);

        return Placeholder.unparsed(key, value);
    }

    /**
     * Creates a placeholder that inserts text and parses any MiniMessage tags it contains.
     *
     * @param key the tag name
     * @param value the text to insert and parse
     * @return a tag resolver for the placeholder
     */
    public static TagResolver parsed(String key, String value) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(value, VALUE_NULL_MESSAGE);

        return Placeholder.parsed(key, value);
    }

    /**
     * Creates a styling placeholder that applies the given style to its contents.
     *
     * @param key the tag name
     * @param style the style to apply
     * @return a tag resolver for the styling placeholder
     */
    public static TagResolver styling(String key, StyleBuilderApplicable... style) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(style, STYLE_NULL_MESSAGE);

        return Placeholder.styling(key, style);
    }

    /**
     * Creates a placeholder that inserts a formatted number.
     *
     * <p>The number format is provided as tag arguments, for example {@code <no:'#.00'>} or
     * {@code <no:'de-DE':'#.00'>}.
     *
     * @param key the tag name
     * @param value the number to format
     * @return a tag resolver for the number placeholder
     */
    public static TagResolver number(String key, Number value) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(value, VALUE_NULL_MESSAGE);

        return Formatter.number(key, value);
    }

    /**
     * Creates a placeholder that inserts a formatted date.
     *
     * <p>The date format is provided as a tag argument, for example {@code <date:'yyyy-MM-dd'>}.
     *
     * @param key the tag name
     * @param value the temporal value to format
     * @return a tag resolver for the date placeholder
     */
    public static TagResolver date(String key, TemporalAccessor value) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(value, VALUE_NULL_MESSAGE);

        return Formatter.date(key, value);
    }

    /**
     * Creates a placeholder that inserts text based on a {@link ChoiceFormat} pattern.
     *
     * <p>The choice pattern is provided as a tag argument, for example
     * {@code <choice:'0#no|1#one|1<many'>}.
     *
     * @param key the tag name
     * @param value the number to choose from
     * @return a tag resolver for the choice placeholder
     */
    public static TagResolver choice(String key, Number value) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(value, VALUE_NULL_MESSAGE);

        return Formatter.choice(key, value);
    }

    /**
     * Creates a placeholder that inserts {@code trueCase} when the value is {@code true}, otherwise
     * {@code falseCase}.
     *
     * <p>Both cases are provided as tag arguments, for example
     * {@code <bool:'enabled':'disabled'>}.
     *
     * @param key the tag name
     * @param value the boolean value
     * @return a tag resolver for the boolean placeholder
     */
    public static TagResolver booleanChoice(String key, boolean value) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);

        return Formatter.booleanChoice(key, value);
    }

    /**
     * Creates a placeholder that inserts a list of components joined together.
     *
     * <p>Optional separators can be provided as tag arguments, for example
     * {@code <items:', ':' and '>}.
     *
     * @param key the tag name
     * @param components the components to join
     * @return a tag resolver for the joining placeholder
     */
    public static TagResolver joining(String key, Iterable<? extends ComponentLike> components) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(components, COMPONENTS_NULL_MESSAGE);

        return Formatter.joining(key, components);
    }

    /**
     * Creates a placeholder that inserts a list of components joined together.
     *
     * @param key the tag name
     * @param components the components to join
     * @return a tag resolver for the joining placeholder
     */
    public static TagResolver joining(String key, ComponentLike... components) {
        Objects.requireNonNull(key, KEY_NULL_MESSAGE);
        Objects.requireNonNull(components, COMPONENTS_NULL_MESSAGE);

        return Formatter.joining(key, components);
    }

    /**
     * Combines multiple tag resolvers into a single resolver.
     *
     * @param resolvers the resolvers to combine
     * @return a combined tag resolver
     */
    public static TagResolver combine(TagResolver... resolvers) {
        Objects.requireNonNull(resolvers, RESOLVERS_NULL_MESSAGE);

        return TagResolver.resolver(resolvers);
    }
}
