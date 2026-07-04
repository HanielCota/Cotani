package com.cotani.user.internal.cache;

import com.cotani.task.util.CompletionStages;
import com.cotani.user.api.CotaniUser;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.*;
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

    public UserCache(UserRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<SimpleCotaniUser> findInternal(UUID uniqueId) {
        return Optional.ofNullable(users.get(uniqueId));
    }

    public Optional<CotaniUser> find(UUID uniqueId) {
        return Optional.ofNullable(users.get(uniqueId)).map(CotaniUser.class::cast);
    }

    public void put(SimpleCotaniUser user) {
        Objects.requireNonNull(user, "user");
        users.put(user.uniqueId(), user);
    }

    public boolean remove(UUID uniqueId, UUID expectedSessionId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(expectedSessionId, "expectedSessionId");
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

    public CompletionStage<Void> save(UUID uniqueId) {
        SimpleCotaniUser user = users.get(uniqueId);

        if (user == null) {
            return CompletionStages.completedVoid();
        }

        var updated = user.withIncrementedVersion();
        return repository.save(updated).thenRun(() -> users.replace(uniqueId, user, updated));
    }

    public CompletionStage<Void> saveAll() {
        var snapshot = List.copyOf(users.values());
        if (snapshot.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        var updated =
                snapshot.stream().map(SimpleCotaniUser::withIncrementedVersion).toList();
        return repository.saveAll(updated).thenRun(() -> updateCacheAfterSave(snapshot, updated));
    }

    private void updateCacheAfterSave(List<SimpleCotaniUser> originals, List<SimpleCotaniUser> updated) {
        for (int i = 0; i < originals.size(); i++) {
            SimpleCotaniUser original = originals.get(i);
            SimpleCotaniUser next = updated.get(i);
            users.replace(original.uniqueId(), original, next);
        }
    }
}
