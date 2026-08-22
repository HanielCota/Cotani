package com.cotani.dialog.api;

import com.cotani.text.MiniMessages;
import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for creating {@link ChatPrompt} instances.
 *
 * @param <T> parsed value type
 */
public final class ChatPromptBuilder<T> {

    private static final String MESSAGE_PARAM = "message";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private @Nullable Component message;
    private Duration timeout = DEFAULT_TIMEOUT;
    private final Set<String> cancelKeywords = new HashSet<>(Set.of("cancel", "cancelar", "sair", "exit"));
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private @Nullable Function<String, Optional<T>> parser;
    private BiFunction<PromptContext, String, Component> invalidInputHandler = (_, input) ->
            MiniMessages.parse("<red>Invalid input '<yellow>" + input + "</yellow>'. Please try again.</red>");
    private @Nullable BiConsumer<Player, CancelReason> cancelHandler;

    public ChatPromptBuilder() {}

    public ChatPromptBuilder<T> message(Component message) {
        this.message = Objects.requireNonNull(message, MESSAGE_PARAM);
        return this;
    }

    public ChatPromptBuilder<T> message(String miniMessage) {
        Objects.requireNonNull(miniMessage, MESSAGE_PARAM);
        this.message = MiniMessages.parse(miniMessage);
        return this;
    }

    public ChatPromptBuilder<T> timeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    public ChatPromptBuilder<T> cancelKeywords(String... keywords) {
        Objects.requireNonNull(keywords, "keywords");
        this.cancelKeywords.clear();
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank()) {
                this.cancelKeywords.add(kw.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return this;
    }

    public ChatPromptBuilder<T> cancelKeywords(Set<String> keywords) {
        Objects.requireNonNull(keywords, "keywords");
        this.cancelKeywords.clear();
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank()) {
                this.cancelKeywords.add(kw.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return this;
    }

    public ChatPromptBuilder<T> maxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        return this;
    }

    public ChatPromptBuilder<T> parser(Function<String, Optional<T>> parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
        return this;
    }

    @SuppressWarnings("unchecked")
    public ChatPromptBuilder<T> filter(Predicate<String> filter, Component errorMessage) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(errorMessage, "errorMessage");
        this.parser = raw -> {
            if (filter.test(raw)) {
                return Optional.of((T) raw);
            }
            return Optional.empty();
        };
        this.invalidInputHandler = (_, _) -> errorMessage;
        return this;
    }

    public ChatPromptBuilder<T> onInvalidInput(Component errorMessage) {
        Objects.requireNonNull(errorMessage, "errorMessage");
        this.invalidInputHandler = (_, _) -> errorMessage;
        return this;
    }

    public ChatPromptBuilder<T> onInvalidInput(BiFunction<PromptContext, String, Component> handler) {
        this.invalidInputHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    public ChatPromptBuilder<T> onCancel(BiConsumer<Player, CancelReason> handler) {
        this.cancelHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    public ChatPrompt<T> build(DialogService dialogService) {
        Objects.requireNonNull(dialogService, "dialogService");
        var resolvedMessage = Objects.requireNonNull(message, "Prompt message must be configured");
        var resolvedParser = Objects.requireNonNull(parser, "Parser must be configured on ChatPromptBuilder");

        return dialogService.createChatPrompt(
                resolvedMessage,
                timeout,
                Set.copyOf(cancelKeywords),
                maxAttempts,
                resolvedParser,
                invalidInputHandler,
                cancelHandler);
    }
}
