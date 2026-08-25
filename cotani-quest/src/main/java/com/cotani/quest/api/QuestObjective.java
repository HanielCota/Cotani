package com.cotani.quest.api;

import java.util.Objects;

/** Immutable objective definition identified by a type and target. */
public record QuestObjective(QuestObjectiveId id, String type, String target, long requiredAmount) {
    public QuestObjective {
        Objects.requireNonNull(id, "id");
        type = normalize(type, "type");
        target = normalize(target, "target");
        if (requiredAmount <= 0) {
            throw new IllegalArgumentException("requiredAmount must be positive");
        }
    }

    private static String normalize(String value, String field) {
        Objects.requireNonNull(value, field);
        var normalized = value.strip().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalArgumentException(field + " must contain between 1 and 128 characters");
        }
        return normalized;
    }
}
