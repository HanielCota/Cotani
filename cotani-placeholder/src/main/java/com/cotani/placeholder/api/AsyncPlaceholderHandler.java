package com.cotani.placeholder.api;

import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Functional handler for asynchronous non-blocking placeholder evaluation.
 */
@FunctionalInterface
@NullMarked
public interface AsyncPlaceholderHandler {

    /**
     * Handles and evaluates the placeholder parameter asynchronously under the given context.
     *
     * @param context async-safe placeholder context
     * @param params arguments/suffix passed to the placeholder
     * @return stage completing with the evaluated string result, or completing with {@code null} if unhandled
     */
    CompletionStage<@Nullable String> handleAsync(AsyncPlaceholderContext context, String params);
}
