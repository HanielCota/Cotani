package com.cotani.command.argument;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Represents a typed, parsed command argument in the command tree.
 *
 * @param <T> the parsed argument value type
 */
public interface Argument<T> {
    /**
     * Returns the name / identifier of the argument.
     *
     * @return argument name
     */
    String name();

    /**
     * Returns the optional human-readable description for usage/help.
     *
     * @return description or empty
     */
    Optional<String> description();

    /**
     * Returns the parser used to convert tokens to {@code T}.
     *
     * @return the argument parser
     */
    ArgumentParser<T> parser();

    /**
     * Returns the suggestion provider for tab completion.
     *
     * @return suggestion provider
     */
    SuggestionProvider suggester();

    /**
     * Checks if this argument is optional.
     *
     * @return {@code true} if optional, {@code false} if required
     */
    boolean isOptional();

    /**
     * Returns the default value if the argument is omitted.
     *
     * @return default value or empty
     */
    Optional<T> defaultValue();

    /**
     * Returns a copy of this argument marked as optional without a default value.
     *
     * @return optional argument
     */
    Argument<T> asOptional();

    /**
     * Returns a copy of this argument marked as optional with the specified default value.
     *
     * @param defaultValue the fallback value when omitted
     * @return optional argument with default
     */
    Argument<T> withDefault(T defaultValue);

    /**
     * Returns a copy of this argument with a new description.
     *
     * @param description the description string
     * @return updated argument
     */
    Argument<T> withDescription(String description);

    /**
     * Returns a copy of this argument with a custom suggestion provider.
     *
     * @param suggester the custom suggestion provider
     * @return updated argument
     */
    Argument<T> withSuggester(SuggestionProvider suggester);

    /**
     * Creates a new required argument.
     *
     * @param name argument name
     * @param parser parsing function
     * @param suggester suggestion function
     * @param <T> value type
     * @return argument definition
     */
    static <T> Argument<T> of(String name, ArgumentParser<T> parser, SuggestionProvider suggester) {
        return new SimpleArgument<>(name, null, parser, suggester, false, null);
    }
}

final class SimpleArgument<T> implements Argument<T> {
    private final String name;
    private final @Nullable String description;
    private final ArgumentParser<T> parser;
    private final SuggestionProvider suggester;
    private final boolean isOptional;
    private final @Nullable T defaultValue;

    SimpleArgument(
            String name,
            @Nullable String description,
            ArgumentParser<T> parser,
            SuggestionProvider suggester,
            boolean isOptional,
            @Nullable T defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
        this.parser = Objects.requireNonNull(parser, "parser");
        this.suggester = Objects.requireNonNull(suggester, "suggester");
        this.isOptional = isOptional;
        this.defaultValue = defaultValue;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    @Override
    public ArgumentParser<T> parser() {
        return parser;
    }

    @Override
    public SuggestionProvider suggester() {
        return suggester;
    }

    @Override
    public boolean isOptional() {
        return isOptional;
    }

    @Override
    public Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    @Override
    public Argument<T> asOptional() {
        return new SimpleArgument<>(name, description, parser, suggester, true, defaultValue);
    }

    @Override
    public Argument<T> withDefault(T defaultValue) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        return new SimpleArgument<>(name, description, parser, suggester, true, defaultValue);
    }

    @Override
    public Argument<T> withDescription(String description) {
        Objects.requireNonNull(description, "description");
        return new SimpleArgument<>(name, description, parser, suggester, isOptional, defaultValue);
    }

    @Override
    public Argument<T> withSuggester(SuggestionProvider suggester) {
        Objects.requireNonNull(suggester, "suggester");
        return new SimpleArgument<>(name, description, parser, suggester, isOptional, defaultValue);
    }
}
