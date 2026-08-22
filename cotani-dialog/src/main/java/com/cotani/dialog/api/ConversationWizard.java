package com.cotani.dialog.api;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

/**
 * Multi-step conversational wizard orchestrating sequential input prompts for a player.
 */
public interface ConversationWizard {

    /**
     * Starts the multi-step conversation wizard for the player.
     *
     * @param player player to run the wizard for
     * @return stage completing with a map containing all collected step answers, or cancelled result
     */
    CompletionStage<PromptResult<Map<String, Object>>> start(Player player);

    /**
     * Creates a new conversation wizard builder.
     *
     * @return new wizard builder
     */
    static ConversationWizardBuilder builder() {
        return new ConversationWizardBuilder();
    }
}
