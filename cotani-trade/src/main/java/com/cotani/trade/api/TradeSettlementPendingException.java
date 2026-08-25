package com.cotani.trade.api;

import java.util.Objects;

/** Indicates that settlement is still pending and must not be retried as a new trade. */
public final class TradeSettlementPendingException extends TradeException {
    private static final long serialVersionUID = 1L;
    private final transient TradeId tradeId;

    public TradeSettlementPendingException(TradeId tradeId) {
        super("Settlement is still pending for trade " + Objects.requireNonNull(tradeId, "tradeId"));
        this.tradeId = tradeId;
    }

    public TradeId tradeId() {
        return tradeId;
    }
}
