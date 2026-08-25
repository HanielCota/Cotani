package com.cotani.permission.internal;

import com.cotani.api.InternalApi;
import com.cotani.permission.api.PermissionDecision;
import com.cotani.permission.api.PermissionGroup;
import com.cotani.permission.api.PermissionNode;
import com.cotani.permission.api.PermissionOrigin;
import com.cotani.permission.api.PermissionRepository;
import com.cotani.permission.api.PermissionService;
import com.cotani.permission.api.PermissionSnapshot;
import com.cotani.permission.api.PermissionState;
import com.cotani.permission.api.PermissionSubjectData;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class InMemoryPermissionService implements PermissionService {
    private static final Comparator<PermissionGroup> GROUP_ORDER =
            Comparator.comparingInt(PermissionGroup::priority).reversed().thenComparing(PermissionGroup::name);

    private final ConcurrentMap<UUID, ConcurrentMap<PermissionNode, PermissionState>> userPermissions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Set<String>> userGroups = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PermissionGroup> groups = new ConcurrentHashMap<>();
    private final Object mutationLock = new Object();
    private final @Nullable PermissionRepository repository;
    private final AtomicBoolean closed = new AtomicBoolean();
    private CompletionStage<Void> persistenceTail = completedVoid();

    public InMemoryPermissionService(List<PermissionGroup> initialGroups) {
        this(new PermissionSnapshot(Map.of(), groupMap(initialGroups)), null);
    }

    public InMemoryPermissionService(PermissionSnapshot initialState, @Nullable PermissionRepository repository) {
        Objects.requireNonNull(initialState, "initialState");
        this.repository = repository;
        initialState.groups().forEach(groups::put);
        initialState.users().forEach((userId, data) -> {
            if (!data.permissions().isEmpty()) {
                userPermissions.put(userId, new ConcurrentHashMap<>(data.permissions()));
            }
            if (!data.groups().isEmpty()) {
                var assignedGroups = ConcurrentHashMap.<String>newKeySet();
                data.groups().forEach(group -> assignedGroups.add(normalizeGroupName(group)));
                userGroups.put(userId, assignedGroups);
            }
        });
    }

    @Override
    public CompletionStage<PermissionDecision> checkAsync(UUID userId, PermissionNode permission) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(permission, "permission");

        synchronized (mutationLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }

            var direct = bestMatch(userAssignments(userId), permission);
            if (direct.isPresent()) {
                return completed(decision(permission, direct.orElseThrow(), PermissionOrigin.USER, "user"));
            }

            var assigned = userGroups.getOrDefault(userId, Set.of());
            var assignedGroups = assigned.stream()
                    .map(groups::get)
                    .filter(Objects::nonNull)
                    .sorted(GROUP_ORDER)
                    .toList();
            for (var group : assignedGroups) {
                var match = bestMatch(group.permissions(), permission);
                if (match.isPresent()) {
                    return completed(decision(permission, match.orElseThrow(), PermissionOrigin.GROUP, group.name()));
                }
            }

            return completed(new PermissionDecision(
                    permission, permission, PermissionState.UNSET, PermissionOrigin.DEFAULT, "default"));
        }
    }

    @Override
    public CompletionStage<Void> setPermissionAsync(UUID userId, PermissionNode permission, PermissionState state) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(state, "state");

        synchronized (mutationLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            if (state == PermissionState.UNSET) {
                var assignments = userPermissions.get(userId);
                if (assignments != null) {
                    assignments.remove(permission);
                    if (assignments.isEmpty()) {
                        userPermissions.remove(userId, assignments);
                    }
                }
                return persistSnapshotLocked();
            }

            userPermissions
                    .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                    .put(permission, state);
            return persistSnapshotLocked();
        }
    }

    @Override
    public CompletionStage<Void> registerGroupAsync(PermissionGroup group) {
        Objects.requireNonNull(group, "group");

        synchronized (mutationLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            groups.put(group.name(), group);
            return persistSnapshotLocked();
        }
    }

    @Override
    public CompletionStage<Void> unregisterGroupAsync(String groupName) {
        Objects.requireNonNull(groupName, "groupName");

        synchronized (mutationLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var normalizedGroupName = normalizeGroupName(groupName);
            groups.remove(normalizedGroupName);
            userGroups.values().forEach(assigned -> assigned.remove(normalizedGroupName));
            return persistSnapshotLocked();
        }
    }

    @Override
    public CompletionStage<Void> assignGroupAsync(UUID userId, String groupName) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(groupName, "groupName");

        synchronized (mutationLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var normalizedGroupName = normalizeGroupName(groupName);
            if (!groups.containsKey(normalizedGroupName)) {
                return failed(
                        new IllegalArgumentException("Permission group is not registered: " + normalizedGroupName));
            }
            userGroups
                    .computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(normalizedGroupName);
            return persistSnapshotLocked();
        }
    }

    @Override
    public CompletionStage<Void> removeGroupAsync(UUID userId, String groupName) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(groupName, "groupName");

        synchronized (mutationLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var assigned = userGroups.get(userId);
            if (assigned != null) {
                assigned.remove(normalizeGroupName(groupName));
                if (assigned.isEmpty()) {
                    userGroups.remove(userId, assigned);
                }
            }
            return persistSnapshotLocked();
        }
    }

    @Override
    public CompletionStage<Set<String>> groupsAsync(UUID userId) {
        Objects.requireNonNull(userId, "userId");

        synchronized (mutationLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(Set.copyOf(userGroups.getOrDefault(userId, Set.of())));
        }
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (mutationLock) {
            if (!closed.compareAndSet(false, true)) {
                return persistenceTail;
            }
            var pendingPersistence = persistenceTail;
            userPermissions.clear();
            userGroups.clear();
            groups.clear();
            return pendingPersistence;
        }
    }

    private static Map<String, PermissionGroup> groupMap(List<PermissionGroup> initialGroups) {
        Objects.requireNonNull(initialGroups, "initialGroups");
        var result = new LinkedHashMap<String, PermissionGroup>();
        initialGroups.forEach(group -> {
            var nonNullGroup = Objects.requireNonNull(group, "initial group");
            result.put(nonNullGroup.name(), nonNullGroup);
        });
        return result;
    }

    private static Optional<PermissionMatch> bestMatch(
            Map<PermissionNode, PermissionState> assignments, PermissionNode requested) {
        return assignments.entrySet().stream()
                .filter(entry -> entry.getKey().matches(requested))
                .max(Comparator.comparingInt((Map.Entry<PermissionNode, PermissionState> entry) ->
                                entry.getKey().specificity())
                        .thenComparing(entry -> entry.getKey().value()))
                .map(entry -> new PermissionMatch(entry.getKey(), entry.getValue()));
    }

    private Map<PermissionNode, PermissionState> userAssignments(UUID userId) {
        var assignments = userPermissions.get(userId);
        return assignments == null ? Map.of() : assignments;
    }

    private PermissionDecision decision(
            PermissionNode requested, PermissionMatch match, PermissionOrigin origin, String sourceId) {
        return new PermissionDecision(requested, match.node(), match.state(), origin, sourceId);
    }

    private CompletionStage<Void> persistSnapshotLocked() {
        if (repository == null) {
            return completedVoid();
        }

        var snapshot = snapshotLocked();
        var previous = persistenceTail;
        persistenceTail = previous.handle((_, _) -> null)
                .thenCompose(_ -> Objects.requireNonNull(repository.saveAsync(snapshot), "repository save stage"));
        return persistenceTail;
    }

    private PermissionSnapshot snapshotLocked() {
        var users = new LinkedHashMap<UUID, PermissionSubjectData>();
        userPermissions
                .keySet()
                .forEach(userId -> users.put(
                        userId,
                        new PermissionSubjectData(userAssignments(userId), userGroups.getOrDefault(userId, Set.of()))));
        userGroups
                .keySet()
                .forEach(userId -> users.putIfAbsent(
                        userId,
                        new PermissionSubjectData(userAssignments(userId), userGroups.getOrDefault(userId, Set.of()))));
        return new PermissionSnapshot(users, new LinkedHashMap<>(groups));
    }

    private static String normalizeGroupName(String groupName) {
        var normalized = groupName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Group name must not be blank");
        }
        return normalized;
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    @SuppressWarnings("NullAway")
    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Permission service is closed");
    }

    private record PermissionMatch(PermissionNode node, PermissionState state) {}
}
