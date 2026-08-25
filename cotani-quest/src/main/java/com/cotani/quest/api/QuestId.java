package com.cotani.quest.api;

import java.util.Locale;
import java.util.Objects;

/** Stable identifier for a registered quest definition. */
public record QuestId(String value) {
    public QuestId {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Quest id must match [a-z0-9][a-z0-9._-]{0,63}");
        }
    }

    public static QuestId of(String value) {
        return new QuestId(value);
    }
}
