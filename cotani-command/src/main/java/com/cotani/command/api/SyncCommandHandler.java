package com.cotani.command.api;

/**
 * Functional handler for synchronous command execution.
 */
@FunctionalInterface
public interface SyncCommandHandler {
    /**
     * Executes the command logic synchronously.
     *
     * @param context the command execution context
     * @throws Exception if an unhandled error occurs
     */
    void execute(CommandContext context) throws Exception;
}
