package com.cotani.location.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds lookup indexes for player homes and global warps. */
public final class CreateLocationIndexesMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani location indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("CREATE INDEX IF NOT EXISTS cotani_locations_owner_name_idx "
                        + "ON cotani_locations (location_type, owner_id, name)")
                .thenCompose(ignored -> schema.execute("CREATE INDEX IF NOT EXISTS cotani_locations_type_name_idx "
                        + "ON cotani_locations (location_type, name)"));
    }
}
