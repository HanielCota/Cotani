package com.cotani.command.argument;

import java.util.List;
import java.util.Objects;
import org.bukkit.command.CommandSender;

/**
 * Context provided during argument parsing.
 *
 * @param sender the command sender
 * @param rawArgs all raw arguments passed by the sender
 * @param currentIndex index of the argument currently being parsed
 */
public record ParseContext(CommandSender sender, List<String> rawArgs, int currentIndex) {
    public ParseContext {
        Objects.requireNonNull(sender, "sender");
        rawArgs = List.copyOf(Objects.requireNonNull(rawArgs, "rawArgs"));
        if (currentIndex < 0) {
            throw new IllegalArgumentException("currentIndex must be non-negative");
        }
    }

    /**
     * Checks if there are more arguments available from the current index.
     *
     * @return {@code true} if arguments remain
     */
    public boolean hasMore() {
        return currentIndex < rawArgs.size();
    }

    /**
     * Checks if no more arguments remain to be parsed from the current index.
     *
     * @return {@code true} if all arguments have been consumed
     */
    public boolean isExhausted() {
        return currentIndex >= rawArgs.size();
    }

    /**
     * Returns the argument token at the current index.
     *
     * @return current token
     * @throws IndexOutOfBoundsException if no more arguments remain
     */
    public String currentArg() {
        if (isExhausted()) {
            throw new IndexOutOfBoundsException("No argument at index " + currentIndex);
        }
        return rawArgs.get(currentIndex);
    }

    /**
     * Returns all remaining arguments starting from {@link #currentIndex}.
     *
     * @return immutable list of remaining arguments
     */
    public List<String> remainingArgs() {
        if (currentIndex >= rawArgs.size()) {
            return List.of();
        }
        return rawArgs.subList(currentIndex, rawArgs.size());
    }
}
