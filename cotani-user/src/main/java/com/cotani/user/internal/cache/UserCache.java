package com.cotani.user.internal.cache;

import com.cotani.api.InternalApi;
import com.cotani.user.api.CotaniUser;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
@InternalApi
public final class UserCache {
    private static final int DEFAULT_MAX_CACHED_USERS = 10_000;
    private static final String UNIQUE_ID_PARAM = "uniqueId";

    private final int maxCachedUsers;
    private final Map<UUID, SimpleCotaniUser> users;
    private final Set<UUID> pinnedUsers;

    public UserCache() {
        this(DEFAULT_MAX_CACHED_USERS);
    }

    public UserCache(int maxCachedUsers) {
        if (maxCachedUsers <= 0) {
            throw new IllegalArgumentException("maxCachedUsers must be positive");
        }

        this.maxCachedUsers = maxCachedUsers;
        this.users = new ConcurrentHashMap<>();
        this.pinnedUsers = ConcurrentHashMap.newKeySet();
    }

    public Optional<SimpleCotaniUser> findInternal(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        return Optional.ofNullable(users.get(uniqueId));
    }

    public Optional<CotaniUser> find(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        return Optional.ofNullable(users.get(uniqueId)).map(CotaniUser.class::cast);
    }

    public Optional<CotaniUser> findByUsername(String username) {
        Objects.requireNonNull(username, "username");

        for (var user : users.values()) {
            if (user.username().equalsIgnoreCase(username)) {
                return Optional.of(user);
            }
        }

        return Optional.empty();
    }

    public void put(SimpleCotaniUser user) {
        Objects.requireNonNull(user, "user");

        users.put(user.uniqueId(), user);
        evictIfNeeded();
    }

    public void pin(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);
        pinnedUsers.add(uniqueId);
    }

    public void unpin(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);
        pinnedUsers.remove(uniqueId);
    }

    public boolean remove(UUID uniqueId, UUID expectedSessionId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);
        Objects.requireNonNull(expectedSessionId, "expectedSessionId");

        var current = users.get(uniqueId);
        if (current == null || !current.sessionId().equals(expectedSessionId)) {
            return false;
        }

        return users.remove(uniqueId, current);
    }

    public Optional<SimpleCotaniUser> updateIfSession(
            UUID uniqueId, UUID expectedSessionId, UnaryOperator<SimpleCotaniUser> updater) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);
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
        pinnedUsers.clear();
        users.clear();
    }

    public boolean contains(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, UNIQUE_ID_PARAM);

        return users.containsKey(uniqueId);
    }

    public Collection<SimpleCotaniUser> allInternal() {
        return List.copyOf(users.values());
    }

    private void evictIfNeeded() {
        if (users.size() <= maxCachedUsers) {
            return;
        }

        var iterator = users.entrySet().iterator();
        while (users.size() > maxCachedUsers && iterator.hasNext()) {
            var entry = iterator.next();
            if (pinnedUsers.contains(entry.getKey())) {
                continue;
            }
            iterator.remove();
        }
    }
}
