package com.cotani.dialog.api;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Interactive Anvil GUI prompt capturing text typed by the player in the rename field.
 */
public interface AnvilPrompt {

    /**
     * Title of the Anvil inventory window.
     *
     * @return window title component
     */
    Component title();

    /**
     * Initial text prepopulated in the Anvil rename input.
     *
     * @return initial text string
     */
    String initialText();

    /**
     * Timeout after which the Anvil prompt is automatically cancelled and closed.
     *
     * @return timeout duration
     */
    Duration timeout();

    /**
     * Opens the Anvil prompt for the target player.
     *
     * @param player player to prompt
     * @return stage completing with the typed string or cancellation
     */
    CompletionStage<PromptResult<String>> open(Player player);

    /**
     * Creates a new builder for configuring an Anvil prompt.
     *
     * @return new anvil prompt builder
     */
    static AnvilPromptBuilder builder() {
        return new AnvilPromptBuilder();
    }
}
