package com.cotani.market.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates listings and purchase receipts used by the SQL marketplace repository. */
public final class CreateMarketTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani market tables";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_market_listings")
                .id("listing_id", ColumnType.STRING)
                .required("seller_id", ColumnType.UUID)
                .required("item_key", ColumnType.STRING)
                .required("item_amount", ColumnType.INT)
                .required("item_data", ColumnType.TEXT)
                .required("currency_id", ColumnType.STRING)
                .required("price", ColumnType.STRING)
                .required("created_at", ColumnType.TIMESTAMP)
                .required("expires_at", ColumnType.TIMESTAMP)
                .required("status", ColumnType.STRING)
                .createIfNotExists()
                .thenCompose(ignored -> schema.table("cotani_market_purchases")
                        .id("purchase_id", ColumnType.STRING)
                        .required("listing_id", ColumnType.STRING)
                        .required("seller_id", ColumnType.UUID)
                        .required("buyer_id", ColumnType.UUID)
                        .required("item_key", ColumnType.STRING)
                        .required("item_amount", ColumnType.INT)
                        .required("item_data", ColumnType.TEXT)
                        .required("currency_id", ColumnType.STRING)
                        .required("price", ColumnType.STRING)
                        .required("reserved_at", ColumnType.TIMESTAMP)
                        .required("status", ColumnType.STRING)
                        .column("settled_at", ColumnType.TIMESTAMP)
                        .createIfNotExists());
    }
}
