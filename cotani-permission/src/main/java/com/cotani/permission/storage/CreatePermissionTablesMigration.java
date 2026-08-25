package com.cotani.permission.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the tables used by {@link StoragePermissionRepository}. */
public final class CreatePermissionTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani permission tables";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_permission_groups")
                .id("group_name", ColumnType.STRING)
                .required("priority", ColumnType.INT)
                .createIfNotExists()
                .thenCompose(_ -> schema.table("cotani_permission_group_nodes")
                        .id("assignment_id", ColumnType.STRING)
                        .required("group_name", ColumnType.STRING)
                        .required("permission", ColumnType.STRING)
                        .required("state", ColumnType.STRING)
                        .createIfNotExists())
                .thenCompose(_ -> schema.table("cotani_permission_user_nodes")
                        .id("assignment_id", ColumnType.STRING)
                        .required("user_id", ColumnType.STRING)
                        .required("permission", ColumnType.STRING)
                        .required("state", ColumnType.STRING)
                        .createIfNotExists())
                .thenCompose(_ -> schema.table("cotani_permission_user_groups")
                        .id("assignment_id", ColumnType.STRING)
                        .required("user_id", ColumnType.STRING)
                        .required("group_name", ColumnType.STRING)
                        .createIfNotExists());
    }
}
