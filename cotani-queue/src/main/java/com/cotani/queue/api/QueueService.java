package com.cotani.queue.api;

import com.cotani.AsyncCloseable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous priority queue and atomic matchmaking use cases.
 *
 * <p>Operations accepted before {@link #closeAsync()} are serialized in submission order. Query
 * stages therefore observe all earlier accepted mutations. New operations are rejected after
 * closing begins. Repository persistence completes before a mutation becomes visible, while
 * event publication is best effort.
 */
public interface QueueService extends AsyncCloseable {
    /** Enqueues one player with a priority and expiration lifetime. */
    CompletionStage<QueueTicket> enqueueAsync(QueueId queueId, UUID playerId, QueueEntryOptions options);

    /** Removes a ticket, returning empty when it no longer exists or has expired. */
    CompletionStage<Optional<QueueTicket>> dequeueAsync(UUID ticketId);

    /** Returns the active ticket for a player, if any, after earlier accepted operations complete. */
    CompletionStage<Optional<QueueTicket>> findByPlayerAsync(UUID playerId);

    /** Returns active tickets in priority/FIFO order for one queue after earlier accepted operations complete. */
    CompletionStage<List<QueueTicket>> entriesAsync(QueueId queueId);

    /**
     * Atomically removes the first {@code requiredPlayers} active tickets and returns a match.
     * Returns empty when the queue does not have enough active tickets.
     */
    CompletionStage<Optional<QueueMatch>> matchAsync(QueueId queueId, int requiredPlayers);
}
