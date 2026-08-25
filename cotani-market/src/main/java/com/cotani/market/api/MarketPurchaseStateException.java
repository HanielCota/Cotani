package com.cotani.market.api;

import java.util.Objects;
import java.util.UUID;

/** Indicates that a purchase receipt cannot be used for another settlement attempt. */
public final class MarketPurchaseStateException extends MarketException {
    private static final long serialVersionUID = 1L;
    private final UUID purchaseId;
    private final MarketPurchaseStatus status;

    public MarketPurchaseStateException(MarketPurchaseId purchaseId, MarketPurchaseStatus status) {
        super("Purchase " + Objects.requireNonNull(purchaseId, "purchaseId").value() + " is in state "
                + Objects.requireNonNull(status, "status"));
        this.purchaseId = purchaseId.value();
        this.status = status;
    }

    public MarketPurchaseId purchaseId() {
        return new MarketPurchaseId(purchaseId);
    }

    public MarketPurchaseStatus status() {
        return status;
    }
}
