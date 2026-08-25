package com.cotani.trade.api;

import com.cotani.AsyncCloseable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous two-player trade orchestration.
 *
 * <p>All operations are serialized per service instance. Persistence must provide the same
 * participant reservation guarantee across service instances. A settlement timeout leaves the
 * trade in {@link TradeStatus#SETTLEMENT_PENDING}; callers must not create a replacement trade
 * for its participants until reconciliation completes. Call {@link #closeAsync()} during plugin
 * shutdown.
 */
public interface TradeService extends AsyncCloseable {
    /** Creates an open trade when neither participant has another active trade. */
    CompletionStage<TradeSession> createAsync(UUID initiatorId, UUID recipientId, TradeOptions options);

    /** Replaces one participant's offer and clears both confirmations. */
    CompletionStage<TradeSession> offerAsync(TradeId tradeId, UUID playerId, List<TradeAsset> assets);

    /**
     * Confirms the current offer revision and settles once both participants confirm.
     *
     * <p>The returned stage may fail with {@link TradeSettlementPendingException} when the
     * settlement deadline expires. That failure does not mean that the assets were not exchanged.
     */
    CompletionStage<TradeSession> confirmAsync(TradeId tradeId, UUID playerId);

    /** Cancels an open trade by one of its participants. */
    CompletionStage<TradeSession> cancelAsync(TradeId tradeId, UUID playerId);

    /** Finds a trade by id, including terminal sessions retained by the repository. */
    CompletionStage<Optional<TradeSession>> findAsync(TradeId tradeId);

    /** Finds the active trade containing one player. */
    CompletionStage<Optional<TradeSession>> findByPlayerAsync(UUID playerId);
}
