package com.cotani.user.internal.repository;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

public final class CreateUsersTableMigration implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani users table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_users")
                .id("unique_id", ColumnType.UUID)
                .required("username", ColumnType.STRING)
                .required("first_join_at", ColumnType.LONG)
                .required("last_join_at", ColumnType.LONG)
                .required("last_quit_at", ColumnType.LONG)
                .required("version", ColumnType.LONG)
                .createIfNotExists();
    }
}
