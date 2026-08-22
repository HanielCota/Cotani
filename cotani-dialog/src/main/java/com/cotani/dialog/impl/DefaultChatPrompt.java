package com.cotani.dialog.impl;

import com.cotani.api.InternalApi;
import com.cotani.dialog.api.CancelReason;
import com.cotani.dialog.api.ChatPrompt;
import com.cotani.dialog.api.PromptContext;
import com.cotani.dialog.api.PromptResult;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultChatPrompt<T> implements ChatPrompt<T>, ActivePrompt {

    private final Component message;
    private final Duration timeout;
    private final Set<String> cancelKeywords;
    private final int maxAttempts;
    private final Function<String, Optional<T>> parser;
    private final BiFunction<PromptContext, String, Component> invalidInputHandler;
    private final @Nullable BiConsumer<Player, CancelReason> cancelHandler;
    private final DefaultDialogService dialogService;
    private final PaperTaskScheduler scheduler;

    private final CompletableFuture<PromptResult<T>> future = new CompletableFuture<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicInteger attemptCounter = new AtomicInteger(1);
    private final Instant startedAt = Instant.now();

    private @Nullable UUID targetPlayerId;
    private @Nullable SchedulerTask timeoutTask;

    public DefaultChatPrompt(
            Component message,
            Duration timeout,
            Set<String> cancelKeywords,
            int maxAttempts,
            Function<String, Optional<T>> parser,
            BiFunction<PromptContext, String, Component> invalidInputHandler,
            @Nullable BiConsumer<Player, CancelReason> cancelHandler,
            DefaultDialogService dialogService,
            PaperTaskScheduler scheduler) {
        this.message = Objects.requireNonNull(message, "message");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.cancelKeywords = Objects.requireNonNull(cancelKeywords, "cancelKeywords");
        this.maxAttempts = maxAttempts;
        this.parser = Objects.requireNonNull(parser, "parser");
        this.invalidInputHandler = Objects.requireNonNull(invalidInputHandler, "invalidInputHandler");
        this.cancelHandler = cancelHandler;
        this.dialogService = Objects.requireNonNull(dialogService, "dialogService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public Component message() {
        return message;
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public Set<String> cancelKeywords() {
        return cancelKeywords;
    }

    @Override
    public int maxAttempts() {
        return maxAttempts;
    }

    @Override
    public UUID playerId() {
        return Objects.requireNonNull(targetPlayerId, "targetPlayerId");
    }

    @Override
    public CompletionStage<PromptResult<T>> start(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        this.targetPlayerId = playerId;

        dialogService.registerActivePrompt(this);

        player.sendMessage(message);

        this.timeoutTask = scheduler.asyncLater(() -> cancel(CancelReason.TIMEOUT), timeout);

        return future.whenComplete((_, _) -> {
            cleanup();
            dialogService.unregisterActivePrompt(playerId, this);
        });
    }

    /**
     * Processes a chat message submitted by the target player.
     *
     * @param player player who typed the message
     * @param rawText raw message content
     */
    public void handleInput(Player player, String rawText) {
        if (finished.get()) {
            return;
        }

        String trimmed = rawText.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (cancelKeywords.contains(lower)) {
            cancel(CancelReason.USER_CANCELLED);
            return;
        }

        int currentAttempt = attemptCounter.get();
        var context = new PromptContext(player, currentAttempt, startedAt);

        Optional<T> parsed;
        try {
            parsed = parser.apply(trimmed);
        } catch (Exception e) {
            complete(PromptResult.error(e));
            return;
        }

        if (parsed.isPresent()) {
            complete(PromptResult.success(parsed.get()));
            return;
        }

        if (attemptCounter.incrementAndGet() > maxAttempts) {
            cancel(CancelReason.MAX_ATTEMPTS_EXCEEDED);
            return;
        }

        Component errorMsg = invalidInputHandler.apply(context, trimmed);
        player.sendMessage(errorMsg);
    }

    @Override
    public void cancel(CancelReason reason) {
        complete(PromptResult.cancelled(reason));
        if (cancelHandler != null && targetPlayerId != null) {
            var server = org.bukkit.Bukkit.getServer();
            if (server != null) {
                var player = server.getPlayer(targetPlayerId);
                if (player != null && player.isOnline()) {
                    scheduler.entity(player, () -> cancelHandler.accept(player, reason));
                }
            }
        }
    }

    private void complete(PromptResult<T> result) {
        if (finished.compareAndSet(false, true)) {
            future.complete(result);
        }
    }

    private void cleanup() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }
}
