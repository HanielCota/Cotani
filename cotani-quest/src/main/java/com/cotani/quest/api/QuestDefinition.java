package com.cotani.quest.api;

import com.cotani.reward.api.RewardId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable quest definition composed of bounded objectives and one reward id. */
public record QuestDefinition(QuestId id, List<QuestObjective> objectives, RewardId rewardId) {
    public QuestDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(objectives, "objectives");
        Objects.requireNonNull(rewardId, "rewardId");
        if (objectives.isEmpty()
                || objectives.size() > 64
                || objectives.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("objectives must contain between 1 and 64 non-null values");
        }
        var ids = new HashSet<QuestObjectiveId>();
        for (var objective : objectives) {
            if (!ids.add(objective.id())) {
                throw new IllegalArgumentException(
                        "Duplicate quest objective id: " + objective.id().value());
            }
        }
        objectives = List.copyOf(objectives);
    }

    /** Finds an objective by id without exposing an internal mutable collection. */
    public Optional<QuestObjective> findObjective(QuestObjectiveId objectiveId) {
        Objects.requireNonNull(objectiveId, "objectiveId");
        return objectives.stream()
                .filter(objective -> objective.id().equals(objectiveId))
                .findFirst();
    }
}
