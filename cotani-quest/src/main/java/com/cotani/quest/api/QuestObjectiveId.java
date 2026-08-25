package com.cotani.quest.api;

import java.util.Locale;
import java.util.Objects;

/** Stable identifier for one objective inside a quest. */
public record QuestObjectiveId(String value) {
    public QuestObjectiveId {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Quest objective id must match [a-z0-9][a-z0-9._-]{0,63}");
        }
    }

    public static QuestObjectiveId of(String value) {
        return new QuestObjectiveId(value);
    }
}
