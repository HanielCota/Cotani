package com.cotani.season.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds indexes used by season progress and operation lookups. */
public final class CreateSeasonIndexesMigration implements Migration {
    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Create Cotani season indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("CREATE INDEX IF NOT EXISTS cotani_seasons_progress_season_idx "
                + "ON cotani_seasons_progress (season_id, experience)");
    }
}
