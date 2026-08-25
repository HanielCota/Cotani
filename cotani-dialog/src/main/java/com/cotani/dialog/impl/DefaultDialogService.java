package com.cotani.dialog.impl;

import com.cotani.api.InternalApi;
import com.cotani.dialog.api.AnvilPrompt;
import com.cotani.dialog.api.AnvilPromptBuilder;
import com.cotani.dialog.api.CancelReason;
import com.cotani.dialog.api.ChatPrompt;
import com.cotani.dialog.api.ChatPromptBuilder;
import com.cotani.dialog.api.ConversationWizard;
import com.cotani.dialog.api.ConversationWizardBuilder;
import com.cotani.dialog.api.DialogService;
import com.cotani.dialog.api.PromptContext;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultDialogService implements DialogService {

    private final PaperTaskScheduler scheduler;
    private final ConcurrentHashMap<UUID, ActivePrompt> activePrompts = new ConcurrentHashMap<>();
    private final DialogChatListener chatListener;
    private final DialogAnvilListener anvilListener;

    public DefaultDialogService(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");

        var pm = plugin.getServer().getPluginManager();
        this.chatListener = new DialogChatListener(this);
        this.anvilListener = new DialogAnvilListener(this);
        pm.registerEvents(chatListener, plugin);
        pm.registerEvents(anvilListener, plugin);
    }

    @Override
    public <T> ChatPromptBuilder<T> chat() {
        return new ChatPromptBuilder<>();
    }

    @Override
    public AnvilPromptBuilder anvil() {
        return new AnvilPromptBuilder();
    }

    @Override
    public ConversationWizardBuilder wizard() {
        return new ConversationWizardBuilder();
    }

    @Override
    public <T> ChatPrompt<T> createChatPrompt(
            Component message,
            Duration timeout,
            Set<String> cancelKeywords,
            int maxAttempts,
            Function<String, Optional<T>> parser,
            BiFunction<PromptContext, String, Component> invalidInputHandler,
            @Nullable BiConsumer<Player, CancelReason> cancelHandler) {
        return new DefaultChatPrompt<>(
                message,
                timeout,
                cancelKeywords,
                maxAttempts,
                parser,
                invalidInputHandler,
                cancelHandler,
                this,
                scheduler);
    }

    @Override
    public AnvilPrompt createAnvilPrompt(Component title, String initialText, Duration timeout, ItemStack leftItem) {
        return new DefaultAnvilPrompt(title, initialText, timeout, leftItem, this, scheduler);
    }

    @Override
    public ConversationWizard createWizard(List<ConversationWizardBuilder.WizardStepDefinition> steps) {
        return new DefaultConversationWizard(steps, this);
    }

    public void registerActivePrompt(ActivePrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");
        var previous = activePrompts.put(prompt.playerId(), prompt);
        if (previous != null && !previous.equals(prompt)) {
            previous.cancel(CancelReason.OVERRIDDEN);
        }
    }

    public void unregisterActivePrompt(UUID playerId, ActivePrompt prompt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(prompt, "prompt");
        activePrompts.remove(playerId, prompt);
    }

    public @Nullable ActivePrompt getActivePrompt(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return activePrompts.get(playerId);
    }

    @Override
    public boolean cancelPrompt(UUID playerId, CancelReason reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reason, "reason");
        var prompt = activePrompts.remove(playerId);
        if (prompt != null) {
            prompt.cancel(reason);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasActivePrompt(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return activePrompts.containsKey(playerId);
    }

    @Override
    public int activePromptsCount() {
        return activePrompts.size();
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        close();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        org.bukkit.event.HandlerList.unregisterAll(chatListener);
        org.bukkit.event.HandlerList.unregisterAll(anvilListener);
        for (var prompt : activePrompts.values()) {
            prompt.cancel(CancelReason.PLUGIN_DISABLE);
        }
        activePrompts.clear();
    }
}
