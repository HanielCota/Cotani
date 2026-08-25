package com.cotani.permission.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot used by permission repositories. */
public record PermissionSnapshot(Map<UUID, PermissionSubjectData> users, Map<String, PermissionGroup> groups) {
    public PermissionSnapshot {
        Objects.requireNonNull(users, "users");
        Objects.requireNonNull(groups, "groups");
        users.forEach((userId, data) -> {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(data, "permission data");
        });
        groups.forEach((name, group) -> {
            Objects.requireNonNull(name, "group name");
            Objects.requireNonNull(group, "permission group");
        });
        users = Map.copyOf(users);
        groups = Map.copyOf(groups);
    }

    public static PermissionSnapshot empty() {
        return new PermissionSnapshot(Map.of(), Map.of());
    }
}
