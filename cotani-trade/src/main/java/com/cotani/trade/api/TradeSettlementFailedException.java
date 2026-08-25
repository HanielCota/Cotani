package com.cotani.trade.api;

import java.util.Objects;

/** Indicates that a durable settlement reconciliation confirmed failure. */
public final class TradeSettlementFailedException extends TradeException {
    private static final long serialVersionUID = 1L;

    public TradeSettlementFailedException(TradeId tradeId) {
        super("Settlement failed for trade " + Objects.requireNonNull(tradeId, "tradeId"));
    }
}
