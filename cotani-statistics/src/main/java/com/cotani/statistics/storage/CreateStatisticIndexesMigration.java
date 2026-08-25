package com.cotani.statistics.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds the index used by bounded statistic rankings. */
public final class CreateStatisticIndexesMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani statistics indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("CREATE INDEX IF NOT EXISTS cotani_statistics_ranking_idx "
                + "ON cotani_statistics (statistic_id, value, player_id)");
    }
}
