package com.cotani.command.api;

/**
 * Defines how a command execution handler should be scheduled and run.
 *
 * <p>This type is intentionally distinct from the task module's {@code ExecutionTarget}, which
 * identifies a Paper/Folia scheduling target such as a region or entity.
 */
public enum CommandExecutionMode {
    /** Executes synchronously on the caller's thread. */
    SYNC,

    /** Executes asynchronously on a background worker thread. */
    ASYNC,

    /** Executes on the player's entity/region thread. */
    ENTITY_REGION
}
