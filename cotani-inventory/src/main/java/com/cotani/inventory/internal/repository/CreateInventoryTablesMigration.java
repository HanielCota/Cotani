package com.cotani.inventory.internal.repository;

import com.cotani.api.InternalApi;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

@InternalApi
public final class CreateInventoryTablesMigration implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani inventory snapshots table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_inventory_snapshots")
                .required("player_id", ColumnType.UUID)
                .required("version", ColumnType.INT)
                .required("created_at", ColumnType.LONG)
                .required("snapshot_data", ColumnType.TEXT)
                .primaryKey("player_id", "created_at")
                .createIfNotExists();
    }
}
