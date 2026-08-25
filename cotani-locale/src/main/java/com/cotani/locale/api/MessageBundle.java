package com.cotani.locale.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/** Immutable set of trusted MiniMessage templates for one locale. */
@NullMarked
public record MessageBundle(LocaleId locale, Map<MessageKey, String> messages) {
    private static final int MAX_MESSAGES = 10_000;
    private static final int MAX_TEMPLATE_LENGTH = 32_768;

    public MessageBundle {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(messages, "messages");
        if (messages.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("A message bundle may contain at most " + MAX_MESSAGES + " messages");
        }

        var normalized = new LinkedHashMap<MessageKey, String>();
        messages.forEach((key, template) -> {
            Objects.requireNonNull(key, "message key");
            Objects.requireNonNull(template, "message template");
            if (template.length() > MAX_TEMPLATE_LENGTH) {
                throw new IllegalArgumentException("Message template exceeds maximum length " + MAX_TEMPLATE_LENGTH);
            }
            normalized.put(key, template);
        });
        messages = Map.copyOf(normalized);
    }

    public static MessageBundle of(LocaleId locale, Map<String, String> messages) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(messages, "messages");
        var converted = new LinkedHashMap<MessageKey, String>();
        messages.forEach((key, value) -> {
            var messageKey = MessageKey.of(key);
            if (converted.containsKey(messageKey)) {
                throw new IllegalArgumentException("Duplicate normalized message key: " + messageKey.value());
            }
            converted.put(messageKey, value);
        });
        return new MessageBundle(locale, converted);
    }

    public Optional<String> find(MessageKey key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(messages.get(key));
    }
}
