package com.cotani.cleanup.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable result of a world scan before any entity is removed. */
public record CleanupScan(
        long scannedEntities,
        long matchedEntities,
        List<CleanupEntitySnapshot> candidates,
        Map<CleanupTarget, Long> matchedByTarget) {
    public CleanupScan(
            long scannedEntities, List<CleanupEntitySnapshot> candidates, Map<CleanupTarget, Long> matchedByTarget) {
        this(scannedEntities, candidates.size(), candidates, matchedByTarget);
    }

    public CleanupScan {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(matchedByTarget, "matchedByTarget");
        candidates = List.copyOf(candidates);
        matchedByTarget = Map.copyOf(matchedByTarget);
        if (scannedEntities < 0
                || matchedEntities < 0
                || matchedByTarget.values().stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("scan counts must not be negative");
        }
        if (matchedEntities
                != matchedByTarget.values().stream().mapToLong(Long::longValue).sum()) {
            throw new IllegalArgumentException("matchedByTarget must describe every matched entity");
        }
        if (matchedEntities > scannedEntities || candidates.size() > matchedEntities) {
            throw new IllegalArgumentException("candidates cannot exceed scannedEntities");
        }
    }

    public boolean truncated() {
        return candidates.size() < matchedEntities;
    }
}
