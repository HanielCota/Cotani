package com.cotani.punishment.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the table used by the SQL punishment repository. */
public final class CreatePunishmentTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani punishment table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_punishments")
                .id("id", ColumnType.UUID)
                .required("target_id", ColumnType.UUID)
                .required("actor_type", ColumnType.STRING)
                .required("actor_id", ColumnType.STRING)
                .required("punishment_type", ColumnType.STRING)
                .required("reason", ColumnType.TEXT)
                .required("created_at", ColumnType.TIMESTAMP)
                .column("expires_at", ColumnType.TIMESTAMP)
                .column("revoked_at", ColumnType.TIMESTAMP)
                .column("revoked_by_type", ColumnType.STRING)
                .column("revoked_by_id", ColumnType.STRING)
                .column("revoked_reason", ColumnType.TEXT)
                .createIfNotExists();
    }
}
