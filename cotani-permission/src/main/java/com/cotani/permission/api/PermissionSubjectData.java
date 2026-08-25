package com.cotani.permission.api;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable permission assignments for one user. */
public record PermissionSubjectData(Map<PermissionNode, PermissionState> permissions, Set<String> groups) {
    public PermissionSubjectData {
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(groups, "groups");
        permissions.forEach((node, state) -> {
            Objects.requireNonNull(node, "permission node");
            Objects.requireNonNull(state, "permission state");
            if (state == PermissionState.UNSET) {
                throw new IllegalArgumentException("Stored permissions must be ALLOW or DENY");
            }
        });
        permissions = Map.copyOf(permissions);
        groups = Set.copyOf(groups);
    }

    public static PermissionSubjectData empty() {
        return new PermissionSubjectData(Map.of(), Set.of());
    }
}
