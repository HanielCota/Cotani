package com.cotani.dialog.api;

import com.cotani.AsyncCloseable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Service managing non-blocking interactive player prompts (Chat, Anvil) and multi-step dialog wizards.
 */
public interface DialogService extends AutoCloseable, AsyncCloseable {

    /**
     * Creates a new chat prompt builder.
     *
     * @param <T> parsed type
     * @return builder instance
     */
    <T> ChatPromptBuilder<T> chat();

    /**
     * Creates a new anvil prompt builder.
     *
     * @return builder instance
     */
    AnvilPromptBuilder anvil();

    /**
     * Creates a new conversation wizard builder.
     *
     * @return builder instance
     */
    ConversationWizardBuilder wizard();

    /**
     * Prompts the player in chat with the given message, returning their raw typed response.
     *
     * @param player player to prompt
     * @param message prompt message
     * @return stage with prompt result
     */
    default CompletionStage<PromptResult<String>> promptChat(Player player, Component message) {
        return ChatPrompt.of(message).build(this).start(player);
    }

    /**
     * Prompts the player in chat with the given MiniMessage text, returning their raw typed response.
     *
     * @param player player to prompt
     * @param miniMessage prompt MiniMessage string
     * @return stage with prompt result
     */
    default CompletionStage<PromptResult<String>> promptChat(Player player, String miniMessage) {
        return this.<String>chat()
                .message(miniMessage)
                .parser(Optional::of)
                .build(this)
                .start(player);
    }

    /**
     * Prompts the player via Anvil GUI with the given title and default text.
     *
     * @param player player to prompt
     * @param title anvil window title
     * @param initialText initial text in rename box
     * @return stage with prompt result
     */
    default CompletionStage<PromptResult<String>> promptAnvil(Player player, Component title, String initialText) {
        return anvil().title(title).initialText(initialText).build(this).open(player);
    }

    /**
     * Internal factory for instantiating a typed {@link ChatPrompt}.
     */
    <T> ChatPrompt<T> createChatPrompt(
            Component message,
            Duration timeout,
            Set<String> cancelKeywords,
            int maxAttempts,
            Function<String, Optional<T>> parser,
            BiFunction<PromptContext, String, Component> invalidInputHandler,
            @Nullable BiConsumer<Player, CancelReason> cancelHandler);

    /**
     * Internal factory for instantiating an {@link AnvilPrompt}.
     */
    AnvilPrompt createAnvilPrompt(Component title, String initialText, Duration timeout, ItemStack leftItem);

    /**
     * Internal factory for instantiating a {@link ConversationWizard}.
     */
    ConversationWizard createWizard(List<ConversationWizardBuilder.WizardStepDefinition> steps);

    /**
     * Cancels any active prompt for the given player.
     *
     * @param playerId player unique ID
     * @param reason cancellation reason
     * @return true if an active prompt was found and cancelled
     */
    boolean cancelPrompt(UUID playerId, CancelReason reason);

    /**
     * Checks if the player currently has an active awaiting prompt.
     *
     * @param playerId player unique ID
     * @return true if active prompt exists
     */
    boolean hasActivePrompt(UUID playerId);

    /**
     * Returns the count of currently active awaiting prompts.
     *
     * @return active prompts count
     */
    int activePromptsCount();

    @Override
    void close();
}
