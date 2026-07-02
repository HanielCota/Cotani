package com.cotani.user.internal.cache;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.user.api.CotaniUser;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for loaded users.
 *
 * <p>New users are created by {@link com.cotani.user.internal.service.SimpleUserService}; this cache never
 * auto-creates a user. Loads are served from memory; persistence is delegated to {@link UserRepository}.
 */
public final class UserCache {

    private final Map<UUID, SimpleCotaniUser> users = new ConcurrentHashMap<>();
    private final UserRepository repository;

    public UserCache(UserRepository repository, PaperTaskScheduler scheduler) {
        this.repository = repository;
    }

    public Optional<SimpleCotaniUser> findInternal(UUID uniqueId) {
        return Optional.ofNullable(users.get(uniqueId));
    }

    public Optional<CotaniUser> find(UUID uniqueId) {
        return Optional.ofNullable(users.get(uniqueId)).map(CotaniUser.class::cast);
    }

    public void put(SimpleCotaniUser user) {
        users.put(user.uniqueId(), user);
    }

    public boolean remove(UUID uniqueId, UUID expectedSessionId) {
        SimpleCotaniUser current = users.get(uniqueId);

        if (current == null || !current.sessionId().equals(expectedSessionId)) {
            return false;
        }

        users.remove(uniqueId);
        return true;
    }

    public void clear() {
        users.clear();
    }

    public boolean contains(UUID uniqueId) {
        return users.containsKey(uniqueId);
    }

    public Collection<SimpleCotaniUser> allInternal() {
        return users.values();
    }

    public void markDirty(UUID uniqueId) {
        // no-op: this cache saves all loaded entries on request.
    }

    public CompletionStage<Void> save(UUID uniqueId) {
        SimpleCotaniUser user = users.get(uniqueId);

        if (user == null) {
            return CompletableFuture.completedStage(null);
        }

        return saveUser(user);
    }

    public CompletionStage<Void> saveAll() {
        var futures = users.values().stream().map(this::saveUser).toList();

        if (futures.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] array =
                futures.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(array).thenApply(_ -> null);
    }

    private CompletionStage<Void> saveUser(SimpleCotaniUser user) {
        var updated = user.withIncrementedVersion();

        return repository.save(updated).thenRun(() -> {
            SimpleCotaniUser current = users.get(user.uniqueId());

            if (current != null && current.version() == user.version()) {
                users.put(user.uniqueId(), updated);
            }
        });
    }
}
