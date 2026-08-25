package com.cotani.trade.api;

/** Durable status returned while reconciling a settlement after a timeout or restart. */
public enum TradeSettlementStatus {
    NOT_STARTED,
    PENDING,
    COMPLETED,
    FAILED,
    UNKNOWN
}
