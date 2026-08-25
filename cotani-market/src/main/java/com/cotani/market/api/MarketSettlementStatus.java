package com.cotani.market.api;

/** Durable state reported by the host settlement adapter for a purchase. */
public enum MarketSettlementStatus {
    NOT_STARTED,
    IN_PROGRESS,
    SETTLED,
    FAILED,
    UNKNOWN
}
