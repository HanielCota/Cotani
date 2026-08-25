package com.cotani.cleanup.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable result returned after region-safe removal attempts. */
public record CleanupRemovalResult(
        long removed,
        long skipped,
        long failed,
        List<CleanupFailure> failures,
        Map<CleanupTarget, Long> removedByTarget) {
    public CleanupRemovalResult(long removed, long skipped, long failed, Map<CleanupTarget, Long> removedByTarget) {
        this(removed, skipped, failed, List.of(), removedByTarget);
    }

    public CleanupRemovalResult {
        Objects.requireNonNull(failures, "failures");
        Objects.requireNonNull(removedByTarget, "removedByTarget");
        failures = List.copyOf(failures);
        removedByTarget = Map.copyOf(removedByTarget);
        if (removed < 0
                || skipped < 0
                || failed < 0
                || failures.size() > failed
                || removedByTarget.values().stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("removal counts must not be negative");
        }
        if (removed
                != removedByTarget.values().stream().mapToLong(Long::longValue).sum()) {
            throw new IllegalArgumentException("removedByTarget must describe every removed entity");
        }
    }

    public static CleanupRemovalResult empty() {
        return new CleanupRemovalResult(0, 0, 0, List.of(), Map.of());
    }
}
