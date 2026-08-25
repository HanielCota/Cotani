package com.cotani.trade.api;

/** Lifecycle state of a trade session. */
public enum TradeStatus {
    OPEN,
    SETTLEMENT_PENDING,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    FAILED
}
