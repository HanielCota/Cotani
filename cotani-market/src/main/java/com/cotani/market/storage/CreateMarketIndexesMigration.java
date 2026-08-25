package com.cotani.market.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds indexes for active browsing, expiry and pending purchase recovery. */
public final class CreateMarketIndexesMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani market indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("CREATE INDEX IF NOT EXISTS cotani_market_active_idx "
                        + "ON cotani_market_listings (status, created_at DESC, listing_id DESC)")
                .thenCompose(ignored -> schema.execute("CREATE INDEX IF NOT EXISTS cotani_market_listing_item_idx "
                        + "ON cotani_market_listings (item_key, currency_id, status)"))
                .thenCompose(ignored -> schema.execute("CREATE INDEX IF NOT EXISTS cotani_market_pending_idx "
                        + "ON cotani_market_purchases (status, reserved_at ASC, purchase_id ASC)"));
    }
}
