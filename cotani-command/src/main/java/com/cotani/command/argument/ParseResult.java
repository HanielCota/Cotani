package com.cotani.command.argument;

import com.cotani.text.MiniMessages;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/**
 * Result of parsing a command argument.
 *
 * @param <T> the type of parsed value
 */
public sealed interface ParseResult<T> permits ParseResult.Success, ParseResult.Failure {

    /**
     * Successful argument parse outcome.
     *
     * @param value the parsed value
     * @param consumedArgs the number of raw argument tokens consumed
     * @param <T> the type of parsed value
     */
    record Success<T>(T value, int consumedArgs) implements ParseResult<T> {
        public Success {
            Objects.requireNonNull(value, "value");
            if (consumedArgs < 1) {
                throw new IllegalArgumentException("consumedArgs must be >= 1");
            }
        }
    }

    /**
     * Failed argument parse outcome with a user-facing error component.
     *
     * @param error the failure message
     * @param <T> the type of parsed value
     */
    record Failure<T>(Component error) implements ParseResult<T> {
        public Failure {
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * Creates a successful parse result consuming 1 argument token.
     *
     * @param value the parsed value
     * @param <T> value type
     * @return success result
     */
    static <T> ParseResult<T> success(T value) {
        return new Success<>(value, 1);
    }

    /**
     * Creates a successful parse result consuming multiple argument tokens.
     *
     * @param value the parsed value
     * @param consumedArgs number of tokens consumed
     * @param <T> value type
     * @return success result
     */
    static <T> ParseResult<T> success(T value, int consumedArgs) {
        return new Success<>(value, consumedArgs);
    }

    /**
     * Creates a failure parse result with an Adventure component error message.
     *
     * @param error the component error message
     * @param <T> value type
     * @return failure result
     */
    static <T> ParseResult<T> failure(Component error) {
        return new Failure<>(error);
    }

    /**
     * Creates a failure parse result from a MiniMessage template.
     *
     * @param miniMessageTemplate the MiniMessage error string
     * @param <T> value type
     * @return failure result
     */
    static <T> ParseResult<T> failure(String miniMessageTemplate) {
        return new Failure<>(MiniMessages.parse(miniMessageTemplate));
    }
}
