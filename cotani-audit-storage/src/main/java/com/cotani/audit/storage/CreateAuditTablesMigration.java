package com.cotani.audit.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the append-only table used by the SQL audit repository. */
public final class CreateAuditTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani audit table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_audit_entries")
                .id("id", ColumnType.STRING)
                .required("occurred_at", ColumnType.TIMESTAMP)
                .required("actor_type", ColumnType.STRING)
                .required("actor_id", ColumnType.STRING)
                .required("action", ColumnType.STRING)
                .required("target_type", ColumnType.STRING)
                .required("target_id", ColumnType.STRING)
                .required("severity", ColumnType.STRING)
                .required("details", ColumnType.TEXT)
                .createIfNotExists();
    }
}
