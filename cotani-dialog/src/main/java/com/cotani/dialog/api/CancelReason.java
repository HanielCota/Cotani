package com.cotani.dialog.api;

/**
 * Reasons why an active dialog or input prompt was cancelled.
 */
public enum CancelReason {
    /**
     * The prompt exceeded its configured timeout duration without receiving valid input.
     */
    TIMEOUT,

    /**
     * The target player disconnected from the server while the prompt was active.
     */
    PLAYER_QUIT,

    /**
     * The player explicitly typed a cancellation keyword (e.g. "cancel", "sair").
     */
    USER_CANCELLED,

    /**
     * A new prompt was opened for the same player, overriding the previous active prompt.
     */
    OVERRIDDEN,

    /**
     * The owning plugin or server was disabled while the prompt was active.
     */
    PLUGIN_DISABLE,

    /**
     * The player exceeded the maximum number of failed input attempts.
     */
    MAX_ATTEMPTS_EXCEEDED
}
