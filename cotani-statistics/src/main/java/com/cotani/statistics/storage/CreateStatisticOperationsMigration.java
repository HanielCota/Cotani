package com.cotani.statistics.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the idempotency ledger for statistic increments. */
public final class CreateStatisticOperationsMigration implements Migration {
    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Create Cotani statistics operation ledger";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_statistics_operations")
                .required("operation_id", ColumnType.UUID)
                .required("player_id", ColumnType.UUID)
                .required("statistic_id", ColumnType.STRING)
                .required("amount", ColumnType.LONG)
                .required("previous_value", ColumnType.LONG)
                .required("value", ColumnType.LONG)
                .required("updated_at", ColumnType.TIMESTAMP)
                .required("revision", ColumnType.LONG)
                .primaryKey("operation_id")
                .createIfNotExists();
    }
}
