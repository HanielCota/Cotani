package com.cotani.party.api;

import java.util.concurrent.CompletionStage;

/** Persistence SPI for party aggregates. Implementations must be idempotent. */
public interface PartyRepository {
    /** Loads the complete party snapshot asynchronously without blocking the caller. */
    CompletionStage<PartySnapshot> loadAsync();

    /**
     * Creates one party, rejecting an existing party with the same id.
     *
     * <p>The initial party revision is zero. Implementations must make the existence check and
     * insert atomic.
     */
    CompletionStage<Void> createAsync(Party party);

    /**
     * Replaces one party only when its currently persisted revision equals {@code expectedRevision}.
     */
    CompletionStage<Void> updateAsync(PartyId partyId, long expectedRevision, Party party);

    /**
     * Deletes one party only when its currently persisted revision equals {@code expectedRevision}.
     * Repeating a deletion of an already absent party must be safe.
     */
    CompletionStage<Void> deleteAsync(PartyId partyId, long expectedRevision);
}
