package com.cotani.command.api;

/**
 * Defines where a command execution handler should be scheduled and run.
 */
public enum ExecutionTarget {
    /**
     * Executes synchronously on the caller's thread (standard Bukkit dispatch thread).
     */
    SYNC,

    /**
     * Executes asynchronously on a background worker thread.
     */
    ASYNC,

    /**
     * Executes on the player's entity/region thread (Paper and Folia safe).
     */
    ENTITY_REGION
}
