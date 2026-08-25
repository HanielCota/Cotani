package com.cotani.season.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the idempotency ledger for season experience operations. */
public final class CreateSeasonExperienceOperationsMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani season experience operation ledger";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_seasons_experience_operations")
                .required("operation_id", ColumnType.UUID)
                .required("player_id", ColumnType.UUID)
                .required("season_id", ColumnType.STRING)
                .required("amount", ColumnType.LONG)
                .required("occurred_at", ColumnType.TIMESTAMP)
                .primaryKey("operation_id")
                .createIfNotExists();
    }
}
