package com.cotani.command.argument;

/**
 * Functional parser converting command arguments into a typed object.
 *
 * @param <T> the parsed value type
 */
@FunctionalInterface
public interface ArgumentParser<T> {
    /**
     * Parses tokens from the given {@link ParseContext}.
     *
     * @param context the parsing context
     * @return parse success or failure
     */
    ParseResult<T> parse(ParseContext context);
}
