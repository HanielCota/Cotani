package com.cotani.permission.api;

import java.util.concurrent.CompletionStage;

/**
 * Persistence boundary for permission state.
 *
 * <p>Implementations must not retain mutable references to the supplied snapshot. Repository
 * operations are asynchronous and must propagate storage failures through their returned stages.
 */
public interface PermissionRepository {
    /** Loads an immutable state snapshot; failures complete the returned stage exceptionally. */
    CompletionStage<PermissionSnapshot> loadAsync();

    /** Persists an immutable state snapshot; implementations must not block the caller. */
    CompletionStage<Void> saveAsync(PermissionSnapshot snapshot);
}
