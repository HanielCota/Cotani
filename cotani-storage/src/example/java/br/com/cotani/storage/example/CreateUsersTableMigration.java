package br.com.cotani.storage.example;

import br.com.cotani.storage.migration.Migration;
import br.com.cotani.storage.schema.ColumnType;
import br.com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

public final class CreateUsersTableMigration implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create users table";
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
