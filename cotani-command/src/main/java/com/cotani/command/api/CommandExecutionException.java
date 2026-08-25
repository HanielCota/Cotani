package com.cotani.command.api;

import java.io.Serial;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Domain exception thrown during command execution or argument evaluation.
 */
public final class CommandExecutionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient @Nullable Component userMessage;

    public CommandExecutionException(String message) {
        super(Objects.requireNonNull(message, "message"));
        this.userMessage = null;
    }

    public CommandExecutionException(String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.userMessage = null;
    }

    public CommandExecutionException(Component userMessage) {
        super("Command execution failed with user message");
        this.userMessage = Objects.requireNonNull(userMessage, "userMessage");
    }

    public CommandExecutionException(Component userMessage, Throwable cause) {
        super("Command execution failed with user message", cause);
        this.userMessage = Objects.requireNonNull(userMessage, "userMessage");
    }

    /**
     * Returns the formatted message intended for the command sender, if provided.
     *
     * @return the user message if present
     */
    public java.util.Optional<Component> userMessage() {
        return java.util.Optional.ofNullable(userMessage);
    }
}
