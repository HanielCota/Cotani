package com.cotani.placeholder.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Functional handler for synchronous placeholder evaluation.
 */
@FunctionalInterface
@NullMarked
public interface PlaceholderHandler {

    /**
     * Handles and evaluates the placeholder parameter under the given context.
     *
     * @param context placeholder context
     * @param params arguments/suffix passed to the placeholder
     * @return evaluated string result, or {@code null} if unhandled
     */
    @Nullable
    String handle(PlaceholderContext context, String params);
}
