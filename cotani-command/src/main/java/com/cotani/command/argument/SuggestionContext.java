package com.cotani.command.argument;

import java.util.List;
import java.util.Objects;
import org.bukkit.command.CommandSender;

/**
 * Context provided when calculating tab-completion suggestions.
 *
 * @param sender the command sender requesting suggestions
 * @param rawArgs the full list of arguments currently typed
 * @param currentInput the partial token currently being typed for this argument
 */
public record SuggestionContext(CommandSender sender, List<String> rawArgs, String currentInput) {
    public SuggestionContext {
        Objects.requireNonNull(sender, "sender");
        rawArgs = List.copyOf(Objects.requireNonNull(rawArgs, "rawArgs"));
        Objects.requireNonNull(currentInput, "currentInput");
    }
}
