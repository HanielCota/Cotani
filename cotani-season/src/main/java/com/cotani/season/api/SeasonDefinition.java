package com.cotani.season.api;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable season schedule and cumulative level track. */
public record SeasonDefinition(SeasonId id, String name, Instant startsAt, Instant endsAt, List<SeasonLevel> levels) {
    public SeasonDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(levels, "levels");
        if (name.isBlank() || name.length() > 96) {
            throw new IllegalArgumentException("name must be non-blank and at most 96 characters");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (levels.isEmpty() || levels.size() > 1_000 || levels.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("levels must contain between 1 and 1000 non-null values");
        }
        var ids = new HashSet<Integer>();
        long previousExperience = -1;
        for (var level : levels) {
            if (!ids.add(level.level())) {
                throw new IllegalArgumentException("Duplicate season level: " + level.level());
            }
            if (level.level() != ids.size()) {
                throw new IllegalArgumentException("levels must be sequential starting at level 1");
            }
            if (level.requiredExperience() < previousExperience) {
                throw new IllegalArgumentException("level experience thresholds must be ordered");
            }
            previousExperience = level.requiredExperience();
        }
        if (levels.getFirst().requiredExperience() != 0) {
            throw new IllegalArgumentException("level 1 must require zero experience");
        }
        levels = List.copyOf(levels);
    }

    public Optional<SeasonLevel> findLevel(int level) {
        if (level <= 0) {
            return Optional.empty();
        }
        return level > levels.size() ? Optional.empty() : Optional.of(levels.get(level - 1));
    }

    public SeasonLevel levelFor(long experience) {
        if (experience < 0) {
            throw new IllegalArgumentException("experience cannot be negative");
        }
        var current = levels.getFirst();
        for (var level : levels) {
            if (level.requiredExperience() > experience) {
                break;
            }
            current = level;
        }
        return current;
    }

    public boolean acceptsExperienceAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }

    public boolean hasStartedAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(startsAt);
    }
}
