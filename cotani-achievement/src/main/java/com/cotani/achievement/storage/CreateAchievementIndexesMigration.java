package com.cotani.achievement.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds indexes used by achievement progress administration and recovery queries. */
public final class CreateAchievementIndexesMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani achievement progress indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("CREATE INDEX IF NOT EXISTS cotani_achievements_progress_achievement_idx "
                + "ON cotani_achievements_progress (achievement_id, unlocked)");
    }
}
