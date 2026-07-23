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
        boolean[] removed = new boolean[1];
        users.computeIfPresent(uniqueId, (id, current) -> {
            if (current.sessionId().equals(expectedSessionId)) {
                removed[0] = true;
                return null;
            }
            return current;
        });
        return removed[0];
    }

    public void clear() {
        users.clear();
    }

    public boolean contains(UUID uniqueId) {
        return users.containsKey(uniqueId);
    }

    public Collection<SimpleCotaniUser> allInternal() {
        return List.copyOf(users.values());
    }

    public CompletionStage<Void> save(UUID uniqueId) {
        SimpleCotaniUser original = users.get(uniqueId);
        if (original == null) {
            return CompletionStages.completedVoid();
        }

        var updated = original.withIncrementedVersion();
        if (!users.replace(uniqueId, original, updated)) {
            return CompletionStages.completedVoid();
        }

        return repository.save(updated);
    }

    public CompletionStage<Void> saveAll() {
        var snapshot = List.copyOf(users.values());
        if (snapshot.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        var updated = new ArrayList<SimpleCotaniUser>(snapshot.size());
        for (SimpleCotaniUser original : snapshot) {
            var next = original.withIncrementedVersion();
            if (users.replace(original.uniqueId(), original, next)) {
                updated.add(next);
            }
        }

        if (updated.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        return repository.saveAll(updated);
    }
}
