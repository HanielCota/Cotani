package com.cotani.cleanup.internal;

import com.cotani.api.InternalApi;
import com.cotani.cleanup.api.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Thread-safe in-memory cleanup executor used by tests and local tooling. */
@InternalApi
public final class InMemoryCleanupExecutor implements CleanupExecutor {
    private final Map<UUID, CleanupEntitySnapshot> entities = new HashMap<>();
    private final CleanupProtection protection;

    public InMemoryCleanupExecutor() {
        this(CleanupProtection.none());
    }

    public InMemoryCleanupExecutor(CleanupProtection protection) {
        this.protection = Objects.requireNonNull(protection, "protection");
    }

    public InMemoryCleanupExecutor(Iterable<CleanupEntitySnapshot> initialEntities) {
        this(initialEntities, CleanupProtection.none());
    }

    public InMemoryCleanupExecutor(Iterable<CleanupEntitySnapshot> initialEntities, CleanupProtection protection) {
        Objects.requireNonNull(initialEntities, "initialEntities");
        this(protection);
        for (var entity : initialEntities) {
            add(entity);
        }
    }

    public synchronized void add(CleanupEntitySnapshot entity) {
        Objects.requireNonNull(entity, "entity");
        entities.put(entity.entityId(), entity);
    }

    public synchronized boolean contains(UUID entityId) {
        Objects.requireNonNull(entityId, "entityId");
        return entities.containsKey(entityId);
    }

    @Override
    public synchronized CompletionStage<CleanupScan> scanAsync(CleanupPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        var candidates = new ArrayList<CleanupEntitySnapshot>();
        var matchedByTarget = new HashMap<CleanupTarget, Long>();
        for (var entity : entities.values()) {
            if (!isEligible(policy, entity)) {
                continue;
            }
            matchedByTarget.merge(entity.target(), 1L, Long::sum);
            if (candidates.size() >= policy.maxEntities()) {
                continue;
            }
            candidates.add(entity);
        }
        var matched =
                matchedByTarget.values().stream().mapToLong(Long::longValue).sum();
        return CompletableFuture.completedFuture(
                new CleanupScan(entities.size(), matched, candidates, matchedByTarget));
    }

    @Override
    public synchronized CompletionStage<CleanupRemovalResult> removeAsync(
            CleanupPolicy policy, List<CleanupEntitySnapshot> candidates) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(candidates, "candidates");
        long skipped = 0;
        var removedByTarget = new HashMap<CleanupTarget, Long>();
        for (var candidate : candidates) {
            var current = entities.get(candidate.entityId());
            if (current == null || !isEligible(policy, current)) {
                skipped++;
                continue;
            }
            entities.remove(candidate.entityId());
            removedByTarget.merge(current.target(), 1L, Long::sum);
        }
        var removed =
                removedByTarget.values().stream().mapToLong(Long::longValue).sum();
        return CompletableFuture.completedFuture(new CleanupRemovalResult(removed, skipped, 0, removedByTarget));
    }

    private boolean isEligible(CleanupPolicy policy, CleanupEntitySnapshot entity) {
        return policy.matches(entity) && !protection.isProtected(entity);
    }
}
