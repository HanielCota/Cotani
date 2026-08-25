package com.cotani.friend.api;

import java.util.concurrent.CompletionStage;

/** Asynchronous persistence boundary for the complete friendship snapshot. */
public interface FriendRepository {
    /** Loads the current immutable snapshot without blocking the caller. */
    CompletionStage<FriendSnapshot> loadAsync();

    /**
     * Persists {@code snapshot} only when the stored revision equals {@code expectedRevision}.
     * The snapshot revision must be exactly {@code expectedRevision + 1}. Implementations must
     * make the revision check and write atomic and reject stale writes without changing state.
     */
    CompletionStage<Void> saveAsync(FriendSnapshot snapshot, long expectedRevision);
}
