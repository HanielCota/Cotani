package com.cotani.location.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the SQL table used by {@link StorageLocationRepository}. */
public final class CreateLocationTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani location table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_locations")
                .id("location_id", ColumnType.STRING)
                .required("location_type", ColumnType.STRING)
                .column("owner_id", ColumnType.UUID)
                .required("name", ColumnType.STRING)
                .required("world_id", ColumnType.UUID)
                .required("x", ColumnType.DOUBLE)
                .required("y", ColumnType.DOUBLE)
                .required("z", ColumnType.DOUBLE)
                .required("yaw", ColumnType.DOUBLE)
                .required("pitch", ColumnType.DOUBLE)
                .required("created_at", ColumnType.TIMESTAMP)
                .required("updated_at", ColumnType.TIMESTAMP)
                .createIfNotExists();
    }
}
