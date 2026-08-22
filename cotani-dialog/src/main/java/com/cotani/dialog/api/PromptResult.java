package com.cotani.dialog.api;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Result outcome of a player input prompt or conversational dialog.
 *
 * @param <T> parsed value type
 */
public sealed interface PromptResult<T> {

    /**
     * Checks if the prompt completed with a successfully parsed value.
     *
     * @return true if successful
     */
    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    /**
     * Checks if the prompt was cancelled due to timeout, disconnect, or user action.
     *
     * @return true if cancelled
     */
    default boolean isCancelled() {
        return this instanceof Cancelled<T>;
    }

    /**
     * Checks if the prompt ended due to an unexpected error or exception.
     *
     * @return true if errored
     */
    default boolean isFailure() {
        return this instanceof Failure<T>;
    }

    /**
     * Checks if the prompt ended due to an unexpected error or exception.
     *
     * @return true if errored
     */
    default boolean isError() {
        return isFailure();
    }

    /**
     * Returns the parsed value as an {@link Optional}.
     *
     * @return optional containing the value if successful, or empty otherwise
     */
    default Optional<T> valueOptional() {
        if (this instanceof Success<T> s) {
            return Optional.of(s.value());
        }
        return Optional.empty();
    }

    /**
     * Returns the value if successful, or throws {@link NoSuchElementException} if cancelled or errored.
     *
     * @return parsed value
     * @throws NoSuchElementException if this result is not a success
     */
    default T valueOrThrow() {
        if (this instanceof Success<T> s) {
            return s.value();
        }
        if (this instanceof Cancelled<T> c) {
            throw new NoSuchElementException("Prompt was cancelled: " + c.reason());
        }
        if (this instanceof Failure<T> f) {
            throw new IllegalStateException("Prompt failed with error", f.cause());
        }
        throw new NoSuchElementException("No value present in PromptResult");
    }

    /**
     * Returns the value if successful, or the given fallback otherwise.
     *
     * @param fallback fallback value
     * @return parsed value or fallback
     */
    default T valueOrElse(T fallback) {
        if (this instanceof Success<T> s) {
            return s.value();
        }
        return fallback;
    }

    /**
     * Executes the given consumer if this result is a success.
     *
     * @param consumer action to execute
     * @return this result
     */
    default PromptResult<T> ifSuccess(Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (this instanceof Success<T> s) {
            consumer.accept(s.value());
        }
        return this;
    }

    /**
     * Executes the given consumer if this prompt was cancelled.
     *
     * @param consumer action to execute with cancellation reason
     * @return this result
     */
    default PromptResult<T> ifCancelled(Consumer<? super CancelReason> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (this instanceof Cancelled<T> c) {
            consumer.accept(c.reason());
        }
        return this;
    }

    /**
     * Maps the parsed value to another type if successful.
     *
     * @param mapper mapping function
     * @param <R> mapped type
     * @return new prompt result with mapped type
     */
    default <R> PromptResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        if (this instanceof Success<T> s) {
            return new Success<>(Objects.requireNonNull(mapper.apply(s.value()), "mapped value"));
        }
        if (this instanceof Cancelled<T> c) {
            return new Cancelled<>(c.reason());
        }
        if (this instanceof Failure<T> f) {
            return new Failure<>(f.cause());
        }
        throw new IllegalStateException("Unknown result type");
    }

    /**
     * Creates a successful prompt result with the given parsed value.
     *
     * @param value parsed result value
     * @param <T> value type
     * @return success result
     */
    static <T> PromptResult<T> success(T value) {
        return new Success<>(Objects.requireNonNull(value, "value"));
    }

    /**
     * Creates a cancelled prompt result with the given reason.
     *
     * @param reason cancellation reason
     * @param <T> value type
     * @return cancelled result
     */
    static <T> PromptResult<T> cancelled(CancelReason reason) {
        return new Cancelled<>(Objects.requireNonNull(reason, "reason"));
    }

    /**
     * Creates an error prompt result with the given throwable cause.
     *
     * @param cause error cause
     * @param <T> value type
     * @return error result
     */
    static <T> PromptResult<T> error(Throwable cause) {
        return new Failure<>(Objects.requireNonNull(cause, "cause"));
    }

    /**
     * Creates a failure prompt result with the given throwable cause.
     *
     * @param cause failure cause
     * @param <T> value type
     * @return failure result
     */
    static <T> PromptResult<T> failure(Throwable cause) {
        return new Failure<>(Objects.requireNonNull(cause, "cause"));
    }

    /**
     * Successful prompt outcome.
     *
     * @param value parsed value
     * @param <T> value type
     */
    record Success<T>(T value) implements PromptResult<T> {
        public Success {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Cancelled prompt outcome.
     *
     * @param reason cancellation cause
     * @param <T> value type
     */
    record Cancelled<T>(CancelReason reason) implements PromptResult<T> {
        public Cancelled {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Failed prompt outcome.
     *
     * @param cause exception cause
     * @param <T> value type
     */
    record Failure<T>(Throwable cause) implements PromptResult<T> {
        public Failure {
            Objects.requireNonNull(cause, "cause");
        }
    }
}
