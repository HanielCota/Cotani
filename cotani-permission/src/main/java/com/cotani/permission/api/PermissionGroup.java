package com.cotani.permission.api;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable group definition used during permission resolution. */
public record PermissionGroup(String name, int priority, Map<PermissionNode, PermissionState> permissions) {
    public PermissionGroup {
        Objects.requireNonNull(name, "name");
        name = name.trim().toLowerCase(Locale.ROOT);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Group name must not be blank");
        }
        Objects.requireNonNull(permissions, "permissions");
        permissions.forEach((node, state) -> {
            Objects.requireNonNull(node, "permission node");
            Objects.requireNonNull(state, "permission state");
            if (state == PermissionState.UNSET) {
                throw new IllegalArgumentException("Group permissions must be ALLOW or DENY");
            }
        });
        permissions = Map.copyOf(permissions);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final Map<PermissionNode, PermissionState> permissions = new LinkedHashMap<>();
        private int priority;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder allow(String permission) {
            return state(permission, PermissionState.ALLOW);
        }

        public Builder deny(String permission) {
            return state(permission, PermissionState.DENY);
        }

        public Builder state(String permission, PermissionState state) {
            var node = PermissionNode.of(permission);
            var value = Objects.requireNonNull(state, "state");
            if (value == PermissionState.UNSET) {
                permissions.remove(node);
                return this;
            }

            permissions.put(node, value);
            return this;
        }

        public PermissionGroup build() {
            return new PermissionGroup(name, priority, permissions);
        }
    }
}
