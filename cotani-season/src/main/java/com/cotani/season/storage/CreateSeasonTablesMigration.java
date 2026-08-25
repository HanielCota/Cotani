package com.cotani.season.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the SQL table used by {@link StorageSeasonRepository}. */
public final class CreateSeasonTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani season progress table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_seasons_progress")
                .required("player_id", ColumnType.UUID)
                .required("season_id", ColumnType.STRING)
                .required("experience", ColumnType.LONG)
                .required("claimed_levels", ColumnType.TEXT)
                .required("revision", ColumnType.LONG)
                .primaryKey("player_id", "season_id")
                .createIfNotExists();
    }
}
