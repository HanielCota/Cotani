package com.cotani.permission.api;

import com.cotani.AsyncCloseable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Resolves user and group permissions without retaining live Bukkit objects.
 *
 * <p>User assignments override group assignments. Groups are evaluated by descending priority;
 * ties are resolved by group name to keep the result deterministic. Within one source, exact
 * nodes outrank wildcard nodes.
 */
public interface PermissionService extends AsyncCloseable {
    /**
     * Resolves one permission without touching Bukkit objects.
     *
     * <p>After {@link #closeAsync()} begins, the returned stage completes exceptionally with an
     * {@link IllegalStateException}.
     */
    CompletionStage<PermissionDecision> checkAsync(UUID userId, PermissionNode permission);

    default CompletionStage<PermissionDecision> checkAsync(UUID userId, String permission) {
        return checkAsync(userId, PermissionNode.of(permission));
    }

    /**
     * Sets or removes a direct user assignment. {@link PermissionState#UNSET} removes it.
     *
     * <p>Mutations are serialized and, when backed by a repository, persisted in submission order.
     */
    CompletionStage<Void> setPermissionAsync(UUID userId, PermissionNode permission, PermissionState state);

    default CompletionStage<Void> allowAsync(UUID userId, String permission) {
        return setPermissionAsync(userId, PermissionNode.of(permission), PermissionState.ALLOW);
    }

    default CompletionStage<Void> denyAsync(UUID userId, String permission) {
        return setPermissionAsync(userId, PermissionNode.of(permission), PermissionState.DENY);
    }

    default CompletionStage<Void> unsetAsync(UUID userId, String permission) {
        return setPermissionAsync(userId, PermissionNode.of(permission), PermissionState.UNSET);
    }

    /** Registers or replaces an immutable group definition. Group names are case-insensitive. */
    CompletionStage<Void> registerGroupAsync(PermissionGroup group);

    /** Removes a group and all of its assignments atomically. */
    CompletionStage<Void> unregisterGroupAsync(String groupName);

    /** Assigns a registered group to a user, failing if the group is unknown. */
    CompletionStage<Void> assignGroupAsync(UUID userId, String groupName);

    /** Removes a group assignment from a user. */
    CompletionStage<Void> removeGroupAsync(UUID userId, String groupName);

    /** Returns an immutable snapshot of the user's group names. */
    CompletionStage<Set<String>> groupsAsync(UUID userId);
}
