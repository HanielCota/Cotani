package com.cotani.command.api;

import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * Functional handler for asynchronous command execution.
 */
@FunctionalInterface
public interface AsyncCommandHandler {
    /**
     * Executes the command logic asynchronously.
     *
     * @param context the command execution context
     * @return a completion stage completing when execution finishes, or null
     * @throws Exception if an unhandled error occurs
     */
    @Nullable
    CompletionStage<?> executeAsync(CommandContext context) throws Exception;
}
