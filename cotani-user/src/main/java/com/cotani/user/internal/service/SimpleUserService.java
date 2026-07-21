package com.cotani.user.internal.service;

import com.cotani.task.util.CompletionStages;
import com.cotani.user.api.CotaniUser;
import com.cotani.user.api.UserNotLoadedException;
import com.cotani.user.internal.cache.UserCache;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class SimpleUserService implements InternalUserService {

    private final UserCache cache;
    private final UserRepository repository;
    private final ConcurrentMap<UUID, CompletionStage<SimpleCotaniUser>> loadingUsers = new ConcurrentHashMap<>();

    public SimpleUserService(UserCache cache, UserRepository repository) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public CompletionStage<Optional<CotaniUser>> findAsync(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Optional<CotaniUser> cached = cache.find(uniqueId);
        if (cached.isPresent()) {
            return CompletableFuture.completedStage(cached);
        }

        CompletionStage<SimpleCotaniUser> ongoing = loadingUsers.get(uniqueId);
        if (ongoing != null) {
            return ongoing.thenApply(Optional::of);
        }

        return repository
                .findByUniqueId(uniqueId)
                .toCompletableFuture()
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(optional -> optional.map(CotaniUser.class::cast));
    }

    @Override
    public CompletionStage<CotaniUser> getOrThrowAsync(UUID uniqueId) {
        return findAsync(uniqueId)
                .thenApply(optional -> optional.orElseThrow(() -> new UserNotLoadedException(uniqueId)));
    }

    @Override
    public CompletionStage<Boolean> isLoadedAsync(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return CompletableFuture.completedStage(cache.contains(uniqueId));
    }

    @Override
    public CompletionStage<SimpleCotaniUser> load(UUID uniqueId, String username) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(username, "username");

        Optional<SimpleCotaniUser> cached = cache.findInternal(uniqueId);
        if (cached.isPresent()) {
            SimpleCotaniUser user = cached.get();
            long now = System.currentTimeMillis();
            SimpleCotaniUser updated = user.withUsername(username).withLastJoinAt(now);
            cache.put(updated);
            return CompletableFuture.completedStage(updated);
        }

        CompletableFuture<SimpleCotaniUser> loadFuture = new CompletableFuture<>();
        CompletionStage<SimpleCotaniUser> ongoing = loadingUsers.putIfAbsent(uniqueId, loadFuture);
        if (ongoing != null) {
            return ongoing;
        }

        long now = System.currentTimeMillis();
        var _ = repository
                .find(uniqueId, username)
                .toCompletableFuture()
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(optionalUser -> {
                    SimpleCotaniUser loaded =
                            optionalUser.orElseGet(() -> SimpleCotaniUser.createNew(uniqueId, username, now));
                    SimpleCotaniUser updated = loaded.withUsername(username).withLastJoinAt(now);
                    cache.put(updated);
                    return updated;
                })
                .whenComplete((result, throwable) -> {
                    loadingUsers.remove(uniqueId);
                    if (throwable != null) {
                        loadFuture.completeExceptionally(throwable);
                    } else {
                        loadFuture.complete(result);
                    }
                });

        return loadFuture;
    }

    @Override
    public CompletionStage<Void> unload(UUID uniqueId) {
        Optional<SimpleCotaniUser> optionalUser = cache.findInternal(uniqueId);

        if (optionalUser.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        SimpleCotaniUser user = optionalUser.get();
        UUID sessionId = user.sessionId();

        cache.put(user.withLastQuitAt(System.currentTimeMillis()));

        return cache.save(uniqueId).thenRun(() -> cache.remove(uniqueId, sessionId));
    }

    @Override
    public CompletionStage<Void> save(UUID uniqueId) {
        if (!cache.contains(uniqueId)) {
            return CompletionStages.completedVoid();
        }

        return cache.save(uniqueId);
    }

    @Override
    public CompletionStage<Void> saveAll() {
        return cache.saveAll();
    }

    public void clearCache() {
        cache.clear();
    }
}
