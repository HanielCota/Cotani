package com.cotani.command.argument;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Functional provider for tab-completion suggestions.
 */
@FunctionalInterface
public interface SuggestionProvider {
    /**
     * Calculates suggestions matching the given context.
     *
     * @param context suggestion context
     * @return immutable list of suggestion strings
     */
    List<String> suggest(SuggestionContext context);

    /**
     * Returns an empty suggestion provider.
     *
     * @return empty provider
     */
    static SuggestionProvider empty() {
        return _ -> List.of();
    }

    /**
     * Creates a suggestion provider from a fixed collection of options, filtered by prefix.
     *
     * @param choices the available choices
     * @return filtered suggestion provider
     */
    static SuggestionProvider of(Collection<String> choices) {
        Objects.requireNonNull(choices, "choices");
        var copy = List.copyOf(choices);
        return context -> {
            var input = context.currentInput().toLowerCase(Locale.ROOT);
            return copy.stream()
                    .filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        };
    }

    /**
     * Creates a suggestion provider from varargs of options.
     *
     * @param choices the available choices
     * @return filtered suggestion provider
     */
    static SuggestionProvider of(String... choices) {
        Objects.requireNonNull(choices, "choices");
        return of(List.of(choices));
    }

    /**
     * Creates a suggestion provider that returns online player names visible to the sender.
     *
     * @return player names provider
     */
    static SuggestionProvider players() {
        return context -> {
            var input = context.currentInput().toLowerCase(Locale.ROOT);
            var server = Bukkit.getServer();
            if (server == null) {
                return List.of();
            }

            var sender = context.sender();
            var playerSender = sender instanceof Player player ? player : null;

            return server.getOnlinePlayers().stream()
                    .filter(target -> playerSender == null || playerSender.canSee(target))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        };
    }
}
