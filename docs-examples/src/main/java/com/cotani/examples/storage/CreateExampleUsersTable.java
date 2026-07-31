package com.cotani.examples.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

public final class CreateExampleUsersTable implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create example users table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("users")
                .id("unique_id", ColumnType.UUID)
                .required("name", ColumnType.STRING)
                .required("coins", ColumnType.LONG)
                .createIfNotExists();
    }
}
