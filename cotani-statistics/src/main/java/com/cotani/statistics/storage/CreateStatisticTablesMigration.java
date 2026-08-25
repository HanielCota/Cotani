package com.cotani.statistics.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the SQL table used by {@link StorageStatisticRepository}. */
public final class CreateStatisticTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani statistics table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_statistics")
                .required("player_id", ColumnType.UUID)
                .required("statistic_id", ColumnType.STRING)
                .required("value", ColumnType.LONG)
                .required("updated_at", ColumnType.TIMESTAMP)
                .required("revision", ColumnType.LONG)
                .primaryKey("player_id", "statistic_id")
                .createIfNotExists();
    }
}
