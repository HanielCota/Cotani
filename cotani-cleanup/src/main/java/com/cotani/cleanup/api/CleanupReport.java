package com.cotani.cleanup.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable operational report suitable for commands, metrics and audit records. */
public record CleanupReport(
        CleanupRequestId requestId,
        CleanupMode mode,
        Instant startedAt,
        Instant completedAt,
        long scannedEntities,
        long matchedEntities,
        long selectedEntities,
        long removedEntities,
        long skippedEntities,
        long failedEntities,
        List<CleanupFailure> failures,
        Map<CleanupTarget, Long> matchedByTarget,
        Map<CleanupTarget, Long> removedByTarget) {
    public CleanupReport {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(matchedByTarget, "matchedByTarget");
        Objects.requireNonNull(removedByTarget, "removedByTarget");
        Objects.requireNonNull(failures, "failures");
        failures = List.copyOf(failures);
        matchedByTarget = Map.copyOf(matchedByTarget);
        removedByTarget = Map.copyOf(removedByTarget);
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt cannot precede startedAt");
        }
        if (scannedEntities < 0
                || matchedEntities < 0
                || selectedEntities < 0
                || removedEntities < 0
                || skippedEntities < 0
                || failedEntities < 0) {
            throw new IllegalArgumentException("report counts must not be negative");
        }
        if (selectedEntities > matchedEntities) {
            throw new IllegalArgumentException("selectedEntities cannot exceed matchedEntities");
        }
        if (removedEntities + skippedEntities + failedEntities > selectedEntities) {
            throw new IllegalArgumentException("removal outcomes cannot exceed selectedEntities");
        }
        if (failures.size() > failedEntities) {
            throw new IllegalArgumentException("failure details cannot exceed failedEntities");
        }
        if (matchedEntities
                != matchedByTarget.values().stream().mapToLong(Long::longValue).sum()) {
            throw new IllegalArgumentException("matchedByTarget must describe matchedEntities");
        }
        if (removedEntities
                != removedByTarget.values().stream().mapToLong(Long::longValue).sum()) {
            throw new IllegalArgumentException("removedByTarget must describe removedEntities");
        }
    }

    public static CleanupReport preview(
            CleanupRequest request, Instant startedAt, Instant completedAt, CleanupScan scan) {
        return new CleanupReport(
                request.id(),
                CleanupMode.PREVIEW,
                startedAt,
                completedAt,
                scan.scannedEntities(),
                scan.matchedEntities(),
                scan.candidates().size(),
                0,
                0,
                0,
                List.of(),
                scan.matchedByTarget(),
                Map.of());
    }

    public static CleanupReport executed(
            CleanupRequest request,
            Instant startedAt,
            Instant completedAt,
            CleanupScan scan,
            CleanupRemovalResult removal) {
        return new CleanupReport(
                request.id(),
                CleanupMode.EXECUTE,
                startedAt,
                completedAt,
                scan.scannedEntities(),
                scan.matchedEntities(),
                scan.candidates().size(),
                removal.removed(),
                removal.skipped(),
                removal.failed(),
                removal.failures(),
                scan.matchedByTarget(),
                removal.removedByTarget());
    }
}
