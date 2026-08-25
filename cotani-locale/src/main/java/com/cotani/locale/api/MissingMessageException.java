package com.cotani.locale.api;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Indicates that a message key could not be found in any locale fallback. */
@NullMarked
@SuppressWarnings("serial")
public final class MissingMessageException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final LocaleId requestedLocale;
    private final MessageKey key;
    private final List<LocaleId> attemptedLocales;

    public MissingMessageException(LocaleId requestedLocale, MessageKey key, List<LocaleId> attemptedLocales) {
        super("Missing localized message '" + key.value() + "' for locale " + requestedLocale.languageTag());
        this.requestedLocale = Objects.requireNonNull(requestedLocale, "requestedLocale");
        this.key = Objects.requireNonNull(key, "key");
        this.attemptedLocales = List.copyOf(Objects.requireNonNull(attemptedLocales, "attemptedLocales"));
    }

    public LocaleId requestedLocale() {
        return requestedLocale;
    }

    public MessageKey key() {
        return key;
    }

    public List<LocaleId> attemptedLocales() {
        return attemptedLocales;
    }
}
