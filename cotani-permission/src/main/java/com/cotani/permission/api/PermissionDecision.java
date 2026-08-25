package com.cotani.permission.api;

import java.util.Objects;

/** Result of resolving one permission request. */
public record PermissionDecision(
        PermissionNode permission,
        PermissionNode matchedPermission,
        PermissionState state,
        PermissionOrigin origin,
        String sourceId) {
    public PermissionDecision {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(matchedPermission, "matchedPermission");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sourceId, "sourceId");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
    }

    /** Returns whether the resolved state allows the requested permission. */
    public boolean allowed() {
        return state == PermissionState.ALLOW;
    }

    /** Returns whether the resolved state explicitly denies the requested permission. */
    public boolean denied() {
        return state == PermissionState.DENY;
    }

    /** Returns whether no user or group assignment matched the request. */
    public boolean isUnset() {
        return state == PermissionState.UNSET;
    }
}
