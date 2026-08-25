package com.cotani.market.api;

/** Lifecycle state of an idempotent marketplace purchase. */
public enum MarketPurchaseStatus {
    PENDING,
    SETTLED,
    CANCELLED
}
