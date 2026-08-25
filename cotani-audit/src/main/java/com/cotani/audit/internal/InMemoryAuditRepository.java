package com.cotani.audit.internal;

import com.cotani.api.InternalApi;
import com.cotani.audit.api.AuditCapacityExceededException;
import com.cotani.audit.api.AuditCursor;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditQuery;
import com.cotani.audit.api.AuditRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@InternalApi
public final class InMemoryAuditRepository implements AuditRepository {
    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final Comparator<AuditEntry> NEWEST_FIRST = Comparator.comparing(AuditEntry::occurredAt)
            .thenComparing(entry -> entry.id().toString())
            .reversed();

    private final List<AuditEntry> entries = new ArrayList<>();
    private final int maxEntries;

    public InMemoryAuditRepository() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryAuditRepository(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized CompletionStage<Void> appendAsync(AuditEntry entry) {
        java.util.Objects.requireNonNull(entry, "entry");
        if (entries.stream().anyMatch(existing -> existing.id().equals(entry.id()))) {
            return CompletableFuture.completedFuture(null);
        }
        if (entries.size() >= maxEntries) {
            return CompletableFuture.failedFuture(new AuditCapacityExceededException(maxEntries));
        }
        entries.add(entry);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletionStage<List<AuditEntry>> queryAsync(AuditQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        var result = entries.stream()
                .filter(entry ->
                        query.action().isEmpty() || query.action().orElseThrow().equals(entry.action()))
                .filter(entry ->
                        query.actor().isEmpty() || query.actor().orElseThrow().equals(entry.actor()))
                .filter(entry ->
                        query.target().isEmpty() || query.target().orElseThrow().equals(entry.target()))
                .filter(entry -> query.from().isEmpty()
                        || !entry.occurredAt().isBefore(query.from().orElseThrow()))
                .filter(entry -> query.until().isEmpty()
                        || !entry.occurredAt().isAfter(query.until().orElseThrow()))
                .filter(entry -> query.before().isEmpty()
                        || isBeforeCursor(entry, query.before().orElseThrow()))
                .sorted(NEWEST_FIRST)
                .limit(query.limit())
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    private static boolean isBeforeCursor(AuditEntry entry, AuditCursor cursor) {
        if (cursor == null) {
            return true;
        }
        return entry.occurredAt().isBefore(cursor.occurredAt())
                || (entry.occurredAt().equals(cursor.occurredAt())
                        && entry.id().toString().compareTo(cursor.id().toString()) < 0);
    }
}
