package com.cotani.user.internal.cache;

import com.cotani.user.api.CotaniUser;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * In-memory cache for loaded users.
 *
 * <p>New users are created by {@link com.cotani.user.internal.service.SimpleUserService}; this cache never
 * auto-creates a user. Loads are served from memory; persistence is delegated to {@link UserRepository}.
 *
 * <p>The cache uses a bounded map that evicts the least-recently-used entry when the maximum size is
 * exceeded, preventing unbounded growth on long-running servers.
 */
@com.cotani.api.InternalApi
public final class UserCache {

    private static final int DEFAULT_MAX_CACHED_USERS = 10_000;

    private final int maxCachedUsers;
    private final Map<UUID, SimpleCotaniUser> users;

    public UserCache() {
        this(DEFAULT_MAX_CACHED_USERS);
    }

    public UserCache(int maxCachedUsers) {
        if (maxCachedUsers <= 0) {
            throw new IllegalArgumentException("maxCachedUsers must be positive");
        }
        this.maxCachedUsers = maxCachedUsers;
        this.users = new ConcurrentHashMap<>();
    }

    public Optional<SimpleCotaniUser> findInternal(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return Optional.ofNullable(users.get(uniqueId));
    }

    public Optional<CotaniUser> find(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return Optional.ofNullable(users.get(uniqueId)).map(CotaniUser.class::cast);
    }

    public void put(SimpleCotaniUser user) {
        Objects.requireNonNull(user, "user");
        users.put(user.uniqueId(), user);
        evictIfNeeded();
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

    public Optional<SimpleCotaniUser> updateIfSession(
            UUID uniqueId, UUID expectedSessionId, UnaryOperator<SimpleCotaniUser> updater) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(expectedSessionId, "expectedSessionId");
        Objects.requireNonNull(updater, "updater");
        var result = new AtomicReference<>(Optional.<SimpleCotaniUser>empty());
        users.computeIfPresent(uniqueId, (_, current) -> {
            if (!current.sessionId().equals(expectedSessionId)) {
                return current;
            }
            SimpleCotaniUser updated = Objects.requireNonNull(updater.apply(current), "updated");
            result.set(Optional.of(updated));
            return updated;
        });
        return Objects.requireNonNull(result.get());
    }

    public void clear() {
        users.clear();
    }

    public boolean contains(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return users.containsKey(uniqueId);
    }

    public Collection<SimpleCotaniUser> allInternal() {
        return List.copyOf(users.values());
    }

    private void evictIfNeeded() {
        while (users.size() > maxCachedUsers) {
            var iterator = users.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }
}
