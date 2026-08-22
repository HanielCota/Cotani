package com.cotani.dialog.api;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Interactive chat-based prompt capturing and validating player text input asynchronously.
 *
 * @param <T> parsed value type
 */
public interface ChatPrompt<T> {

    /**
     * The prompt instruction message displayed to the player.
     *
     * @return component message
     */
    Component message();

    /**
     * Timeout after which the prompt is cancelled if no valid response is received.
     *
     * @return timeout duration
     */
    Duration timeout();

    /**
     * Keywords that immediately trigger user cancellation when typed by the player.
     *
     * @return set of lowercase cancel keywords
     */
    Set<String> cancelKeywords();

    /**
     * Maximum invalid attempts allowed before the prompt fails.
     *
     * @return maximum attempts allowed
     */
    int maxAttempts();

    /**
     * Starts the chat prompt for the target player.
     *
     * @param player player to prompt
     * @return stage completing with the prompt result
     */
    CompletionStage<PromptResult<T>> start(Player player);

    /**
     * Creates a new builder for configuring a typed chat prompt.
     *
     * @param <T> value type
     * @return new chat prompt builder
     */
    static <T> ChatPromptBuilder<T> builder() {
        return new ChatPromptBuilder<>();
    }

    /**
     * Creates a simple String-based chat prompt with the given message.
     *
     * @param promptMessage message displayed to the player
     * @return new chat prompt builder for strings
     */
    static ChatPromptBuilder<String> of(Component promptMessage) {
        return new ChatPromptBuilder<String>().message(promptMessage).parser(java.util.Optional::of);
    }
}
