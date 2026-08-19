package com.cotani.user.internal.service;

import com.cotani.api.InternalApi;
import com.cotani.task.util.CompletionStages;
import com.cotani.user.api.CotaniUser;
import com.cotani.user.api.UserNotLoadedException;
import com.cotani.user.internal.cache.UserCache;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@InternalApi
public final class SimpleUserService implements InternalUserService {
    private static final String UNIQUE_ID_PARAM = "uniqueId";

    private final UserCache cache;
    private final UserRepository repository;
    private final ConcurrentMap<UUID, CompletableFuture<SimpleCotaniUser>> loadingUsers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, WriteLane> writeLanes = new ConcurrentHashMap<>();
    private final AtomicLong cacheGeneration = new AtomicLong();

    public SimpleUserService(UserCache cache, UserRepository repository) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public CompletionStage<Optional<CotaniUser>> findAsync(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        Optional<CotaniUser> cached = cache.find(uniqueId);

        if (cached.isPresent()) {
            return CompletableFuture.completedStage(cached);
        }

        CompletableFuture<SimpleCotaniUser> ongoing = loadingUsers.get(uniqueId);

        if (ongoing != null) {
            return ongoing.thenApply(Optional::of);
        }

        return repository
                .findByUniqueId(uniqueId)
                .toCompletableFuture()
                .copy()
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(optional -> optional.map(CotaniUser.class::cast));
    }

    @Override
    public CompletionStage<Optional<CotaniUser>> findByNameAsync(String username) {
        Objects.requireNonNull(username, "username");

        Optional<CotaniUser> cached = cache.findByUsername(username);

        if (cached.isPresent()) {
            return CompletableFuture.completedStage(cached);
        }

        return repository
                .findByUsername(username)
                .toCompletableFuture()
                .copy()
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(optional -> optional.map(CotaniUser.class::cast));
    }

    @Override
    public CompletionStage<CotaniUser> getOrThrowAsync(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        return findAsync(uniqueId)
                .thenApply(optional -> optional.orElseThrow(() -> new UserNotLoadedException(uniqueId)));
    }

    @Override
    public CompletionStage<Boolean> isLoadedAsync(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        return CompletableFuture.completedStage(cache.contains(uniqueId));
    }

    @Override
    public Optional<CotaniUser> findCached(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        return cache.find(uniqueId);
    }

    @Override
    public boolean isLoaded(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        return cache.contains(uniqueId);
    }

    @Override
    public CompletionStage<SimpleCotaniUser> load(UUID uniqueId, String username) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);
        Objects.requireNonNull(username, "username");

        Optional<SimpleCotaniUser> cached = cache.findInternal(uniqueId);

        if (cached.isPresent()) {
            SimpleCotaniUser user = cached.get();
            long now = System.currentTimeMillis();
            SimpleCotaniUser updated =
                    user.withUsername(username).withLastJoinAt(now).withNewSessionId();
            activeSessions.put(uniqueId, updated.sessionId());
            cache.put(updated);

            return CompletableFuture.completedStage(updated);
        }

        CompletableFuture<SimpleCotaniUser> loadFuture = new CompletableFuture<>();
        CompletableFuture<SimpleCotaniUser> ongoing = loadingUsers.putIfAbsent(uniqueId, loadFuture);

        if (ongoing != null) {
            return ongoing;
        }

        long now = System.currentTimeMillis();
        long generationAtStart = cacheGeneration.get();
        var _ = repository
                .find(uniqueId, username)
                .toCompletableFuture()
                .copy()
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(optionalUser -> {
                    SimpleCotaniUser loaded =
                            optionalUser.orElseGet(() -> SimpleCotaniUser.createNew(uniqueId, username, now));
                    SimpleCotaniUser updated =
                            loaded.withUsername(username).withLastJoinAt(now).withNewSessionId();
                    activeSessions.put(uniqueId, updated.sessionId());
                    if (cacheGeneration.get() == generationAtStart) {
                        cache.put(updated);
                    }
                    return updated;
                })
                .whenComplete((result, throwable) -> {
                    loadingUsers.remove(uniqueId, loadFuture);
                    if (throwable != null) {
                        loadFuture.completeExceptionally(throwable);
                        return;
                    }

                    loadFuture.complete(result);
                });

        return loadFuture;
    }

    @Override
    public CompletionStage<Void> unload(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        UUID sessionId = activeSessions.remove(uniqueId);
        if (sessionId == null) {
            Optional<SimpleCotaniUser> optionalUser = cache.findInternal(uniqueId);
            if (optionalUser.isEmpty()) {
                return CompletionStages.completedVoid();
            }
            sessionId = optionalUser.get().sessionId();
        }

        UUID finalSessionId = sessionId;
        Optional<SimpleCotaniUser> quitting = cache.updateIfSession(
                uniqueId,
                finalSessionId,
                current -> current.withLastQuitAt(System.currentTimeMillis()).withIncrementedVersion());
        if (quitting.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        return persistSequentially(uniqueId, () -> repository.save(quitting.get()))
                .thenRun(() -> cache.remove(uniqueId, finalSessionId));
    }

    @Override
    public CompletionStage<Void> save(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        UUID sessionId = activeSessions.get(uniqueId);
        if (sessionId == null) {
            Optional<SimpleCotaniUser> optionalUser = cache.findInternal(uniqueId);
            if (optionalUser.isEmpty()) {
                return CompletionStages.completedVoid();
            }
            sessionId = optionalUser.get().sessionId();
        }

        var updated = cache.updateIfSession(uniqueId, sessionId, current -> current.withIncrementedVersion());
        if (updated.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        return persistSequentially(uniqueId, () -> repository.save(updated.get()));
    }

    @Override
    public CompletionStage<Void> saveAll() {
        var snapshot = cache.allInternal();

        if (snapshot.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        var updatedUsers = new ArrayList<SimpleCotaniUser>(snapshot.size());

        for (SimpleCotaniUser original : snapshot) {
            var updated = cache.updateIfSession(
                    original.uniqueId(), original.sessionId(), current -> current.withIncrementedVersion());
            updated.ifPresent(updatedUsers::add);
        }

        if (updatedUsers.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        return repository.saveAll(updatedUsers);
    }

    public void clearCache() {
        cacheGeneration.incrementAndGet();
        activeSessions.clear();
        cache.clear();
    }

    private CompletionStage<Void> persistSequentially(UUID uniqueId, Supplier<CompletionStage<Void>> persistence) {
        var ticketRef = new AtomicReference<WriteTicket>();
        var laneRef = new AtomicReference<WriteLane>();
        writeLanes.compute(uniqueId, (_, current) -> {
            var lane = current == null ? new WriteLane() : current;
            laneRef.set(lane);
            ticketRef.set(lane.enqueue(persistence));

            return lane;
        });

        var ticket = Objects.requireNonNull(ticketRef.get(), "write ticket");
        var lane = Objects.requireNonNull(laneRef.get(), "write lane");
        var _ = ticket.result()
                .whenComplete((_, _) -> writeLanes.computeIfPresent(
                        uniqueId,
                        (_, current) -> current.equals(lane) && current.isIdle(ticket.sequence()) ? null : current));

        return ticket.result();
    }

    private static final class WriteLane {
        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
        private long sequence;

        synchronized WriteTicket enqueue(Supplier<CompletionStage<Void>> persistence) {
            tail = tail.handle((_, _) -> null)
                    .thenCompose(_ -> {
                        try {
                            return Objects.requireNonNull(persistence.get(), "repository persistence returned null");
                        } catch (RuntimeException failure) {
                            return CompletableFuture.failedFuture(failure);
                        }
                    })
                    .toCompletableFuture();
            sequence++;

            return new WriteTicket(tail, sequence);
        }

        synchronized boolean isIdle(long completedSequence) {
            return sequence == completedSequence && tail.isDone();
        }
    }

    private record WriteTicket(CompletableFuture<Void> result, long sequence) {}
}
