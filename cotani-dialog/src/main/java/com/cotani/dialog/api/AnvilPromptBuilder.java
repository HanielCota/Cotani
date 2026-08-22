package com.cotani.dialog.api;

import com.cotani.text.MiniMessages;
import java.time.Duration;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for creating {@link AnvilPrompt} instances.
 */
public final class AnvilPromptBuilder {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private Component title = Component.text("Input");
    private String initialText = "";
    private Duration timeout = DEFAULT_TIMEOUT;
    private @Nullable ItemStack leftItem;

    public AnvilPromptBuilder() {}

    public AnvilPromptBuilder title(Component title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    public AnvilPromptBuilder title(String miniMessage) {
        Objects.requireNonNull(miniMessage, "miniMessage");
        this.title = MiniMessages.parse(miniMessage);
        return this;
    }

    public AnvilPromptBuilder initialText(String initialText) {
        this.initialText = Objects.requireNonNull(initialText, "initialText");
        return this;
    }

    public AnvilPromptBuilder timeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    public AnvilPromptBuilder leftItem(ItemStack leftItem) {
        this.leftItem = Objects.requireNonNull(leftItem, "leftItem").clone();
        return this;
    }

    public AnvilPrompt build(DialogService dialogService) {
        Objects.requireNonNull(dialogService, "dialogService");
        ItemStack item = leftItem != null ? leftItem.clone() : new ItemStack(Material.PAPER);
        return dialogService.createAnvilPrompt(title, initialText, timeout, item);
    }
}
