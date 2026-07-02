package com.cotani.user.internal.service;

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

public final class SimpleUserService implements InternalUserService {

    private final UserCache cache;
    private final UserRepository repository;

    public SimpleUserService(UserCache cache, UserRepository repository) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public CompletionStage<Optional<CotaniUser>> findAsync(UUID uniqueId) {
        return CompletableFuture.completedStage(cache.find(uniqueId));
    }

    @Override
    public CompletionStage<CotaniUser> getOrThrowAsync(UUID uniqueId) {
        return findAsync(uniqueId)
                .thenApply(optional -> optional.orElseThrow(() -> new UserNotLoadedException(uniqueId)));
    }

    @Override
    public CompletionStage<Boolean> isLoadedAsync(UUID uniqueId) {
        return CompletableFuture.completedStage(cache.contains(uniqueId));
    }

    @Override
    public CompletionStage<SimpleCotaniUser> load(UUID uniqueId, String username) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(username, "username");

        long now = System.currentTimeMillis();

        return repository.find(uniqueId, username).thenApply(optionalUser -> {
            SimpleCotaniUser loaded = optionalUser.orElseGet(() -> SimpleCotaniUser.createNew(uniqueId, username, now));
            SimpleCotaniUser updated = loaded.withUsername(username).withLastJoinAt(now);

            cache.put(updated);
            return updated;
        });
    }

    @Override
    public CompletionStage<Void> unload(UUID uniqueId) {
        Optional<SimpleCotaniUser> optionalUser = cache.findInternal(uniqueId);

        if (optionalUser.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }

        SimpleCotaniUser user = optionalUser.get();
        UUID sessionId = user.sessionId();

        cache.put(user.withLastQuitAt(System.currentTimeMillis()));

        return cache.save(uniqueId).thenRun(() -> cache.remove(uniqueId, sessionId));
    }

    @Override
    public CompletionStage<Void> save(UUID uniqueId) {
        if (!cache.contains(uniqueId)) {
            return CompletableFuture.completedStage(null);
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
