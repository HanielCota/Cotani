package com.cotani.cooldown.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

public final class CreateCooldownsTableMigration implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani cooldowns table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_cooldowns")
                .id("cooldown_id", ColumnType.STRING)
                .required("target_type", ColumnType.STRING)
                .required("target_id", ColumnType.STRING)
                .required("action_name", ColumnType.STRING)
                .required("started_at", ColumnType.TIMESTAMP)
                .required("expires_at", ColumnType.TIMESTAMP)
                .createIfNotExists();
    }
}
