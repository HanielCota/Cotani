package com.cotani.permission.storage;

import com.cotani.permission.api.PermissionAssignments;
import com.cotani.permission.api.PermissionGroup;
import com.cotani.permission.api.PermissionNode;
import com.cotani.permission.api.PermissionRepository;
import com.cotani.permission.api.PermissionSnapshot;
import com.cotani.permission.api.PermissionState;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.SqlConsumer;
import com.cotani.storage.transaction.TransactionContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** SQL-backed permission repository using Cotani Storage transactions. */
public final class StoragePermissionRepository implements PermissionRepository {
    private final CotaniStorage storage;

    public StoragePermissionRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<PermissionSnapshot> loadAsync() {
        var groupRows = storage.queryExecutor()
                .queryMany(
                        "SELECT group_name, priority FROM cotani_permission_groups",
                        _ -> {},
                        row -> new GroupRow(row.getString("group_name"), row.getInt("priority")));
        var groupNodeRows = storage.queryExecutor()
                .queryMany(
                        "SELECT group_name, permission, state FROM cotani_permission_group_nodes",
                        _ -> {},
                        row -> new GroupNodeRow(
                                row.getString("group_name"),
                                PermissionNode.of(row.getString("permission")),
                                PermissionState.valueOf(row.getString("state"))));
        var userNodeRows = storage.queryExecutor()
                .queryMany(
                        "SELECT user_id, permission, state FROM cotani_permission_user_nodes",
                        _ -> {},
                        row -> new UserNodeRow(
                                UUID.fromString(row.getString("user_id")),
                                PermissionNode.of(row.getString("permission")),
                                PermissionState.valueOf(row.getString("state"))));
        var userGroupRows = storage.queryExecutor()
                .queryMany(
                        "SELECT user_id, group_name FROM cotani_permission_user_groups",
                        _ -> {},
                        row -> new UserGroupRow(
                                UUID.fromString(row.getString("user_id")), row.getString("group_name")));

        return groupRows
                .thenCombine(groupNodeRows, GroupRows::new)
                .thenCombine(userNodeRows, LoadedRows::new)
                .thenCombine(
                        userGroupRows, (loaded, userGroups) -> toSnapshot(loaded.groups(), loaded.users(), userGroups));
    }

    @Override
    public CompletionStage<Void> saveAsync(PermissionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        return storage.transactions()
                .run(tx -> tx.update("DELETE FROM cotani_permission_group_nodes", _ -> {})
                        .thenCompose(_ -> tx.update("DELETE FROM cotani_permission_groups", _ -> {}))
                        .thenCompose(_ -> tx.update("DELETE FROM cotani_permission_user_nodes", _ -> {}))
                        .thenCompose(_ -> tx.update("DELETE FROM cotani_permission_user_groups", _ -> {}))
                        .thenCompose(_ -> batchOrComplete(
                                tx,
                                "INSERT INTO cotani_permission_groups (group_name, priority) VALUES (?, ?)",
                                groupBinders(snapshot)))
                        .thenCompose(_ -> batchOrComplete(
                                tx,
                                "INSERT INTO cotani_permission_group_nodes (assignment_id, group_name, permission, state) VALUES (?, ?, ?, ?)",
                                groupNodeBinders(snapshot)))
                        .thenCompose(_ -> batchOrComplete(
                                tx,
                                "INSERT INTO cotani_permission_user_nodes (assignment_id, user_id, permission, state) VALUES (?, ?, ?, ?)",
                                userNodeBinders(snapshot)))
                        .thenCompose(_ -> batchOrComplete(
                                tx,
                                "INSERT INTO cotani_permission_user_groups (assignment_id, user_id, group_name) VALUES (?, ?, ?)",
                                userGroupBinders(snapshot))));
    }

    public static List<Migration> migrations() {
        return List.of(new CreatePermissionTablesMigration());
    }

    private static CompletionStage<Void> batchOrComplete(
            TransactionContext transaction, String sql, List<SqlConsumer<ParameterBinder>> binders) {
        if (binders.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return transaction.batch(sql, binders);
    }

    private static PermissionSnapshot toSnapshot(
            GroupRows groups, List<UserNodeRow> userNodes, List<UserGroupRow> userGroups) {
        var groupDefinitions = new LinkedHashMap<String, PermissionGroup.Builder>();
        groups.groups()
                .forEach(row -> groupDefinitions.put(
                        normalizeGroupName(row.name()),
                        PermissionGroup.builder(row.name()).priority(row.priority())));
        groups.nodes()
                .forEach(row -> groupDefinitions
                        .computeIfAbsent(normalizeGroupName(row.groupName()), name -> PermissionGroup.builder(name))
                        .state(row.permission().value(), row.state()));

        var groupMap = new LinkedHashMap<String, PermissionGroup>();
        groupDefinitions.forEach((name, builder) -> groupMap.put(name, builder.build()));

        var userPermissions = new LinkedHashMap<UUID, Map<PermissionNode, PermissionState>>();
        userNodes.forEach(row -> userPermissions
                .computeIfAbsent(row.userId(), ignored -> new LinkedHashMap<>())
                .put(row.permission(), row.state()));
        var userGroupMap = new LinkedHashMap<UUID, Set<String>>();
        userGroups.forEach(row -> userGroupMap
                .computeIfAbsent(row.userId(), ignored -> new java.util.LinkedHashSet<>())
                .add(normalizeGroupName(row.groupName())));

        var users = new LinkedHashMap<UUID, PermissionAssignments>();
        userPermissions
                .keySet()
                .forEach(userId -> users.put(
                        userId,
                        new PermissionAssignments(
                                userPermissions.getOrDefault(userId, Map.of()),
                                userGroupMap.getOrDefault(userId, Set.of()))));
        userGroupMap
                .keySet()
                .forEach(userId -> users.putIfAbsent(
                        userId, new PermissionAssignments(Map.of(), userGroupMap.getOrDefault(userId, Set.of()))));
        return new PermissionSnapshot(users, groupMap);
    }

    private static List<SqlConsumer<ParameterBinder>> groupBinders(PermissionSnapshot snapshot) {
        return snapshot.groups().values().stream()
                .map(group -> (SqlConsumer<ParameterBinder>)
                        binder -> binder.string(group.name()).integer(group.priority()))
                .toList();
    }

    private static List<SqlConsumer<ParameterBinder>> groupNodeBinders(PermissionSnapshot snapshot) {
        var binders = new ArrayList<SqlConsumer<ParameterBinder>>();
        snapshot.groups()
                .values()
                .forEach(group -> group.permissions()
                        .forEach((node, state) -> binders.add(
                                binder -> binder.string(assignmentId("group-node", group.name(), node.value()))
                                        .string(group.name())
                                        .string(node.value())
                                        .string(state.name()))));
        return binders;
    }

    private static List<SqlConsumer<ParameterBinder>> userNodeBinders(PermissionSnapshot snapshot) {
        var binders = new ArrayList<SqlConsumer<ParameterBinder>>();
        snapshot.users()
                .forEach((userId, data) -> data.permissions()
                        .forEach((node, state) -> binders.add(
                                binder -> binder.string(assignmentId("user-node", userId.toString(), node.value()))
                                        .string(userId.toString())
                                        .string(node.value())
                                        .string(state.name()))));
        return binders;
    }

    private static List<SqlConsumer<ParameterBinder>> userGroupBinders(PermissionSnapshot snapshot) {
        var binders = new ArrayList<SqlConsumer<ParameterBinder>>();
        snapshot.users()
                .forEach((userId, data) -> data.groups()
                        .forEach(group -> binders.add(
                                binder -> binder.string(assignmentId("user-group", userId.toString(), group))
                                        .string(userId.toString())
                                        .string(group))));
        return binders;
    }

    private static String assignmentId(String scope, String... values) {
        var material = scope + "\u0000" + String.join("\u0000", values);
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String normalizeGroupName(String name) {
        var normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Group name must not be blank");
        }
        return normalized;
    }

    private record GroupRow(String name, int priority) {}

    private record GroupNodeRow(String groupName, PermissionNode permission, PermissionState state) {}

    private record UserNodeRow(UUID userId, PermissionNode permission, PermissionState state) {}

    private record UserGroupRow(UUID userId, String groupName) {}

    private record GroupRows(List<GroupRow> groups, List<GroupNodeRow> nodes) {}

    private record LoadedRows(GroupRows groups, List<UserNodeRow> users) {}
}
