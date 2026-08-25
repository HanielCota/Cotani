package com.cotani.achievement.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the SQL table used by {@link StorageAchievementRepository}. */
public final class CreateAchievementTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani achievement progress table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_achievements_progress")
                .required("player_id", ColumnType.UUID)
                .required("achievement_id", ColumnType.STRING)
                .required("unlocked", ColumnType.BOOLEAN)
                .column("unlocked_at", ColumnType.TIMESTAMP)
                .column("reward_claim_id", ColumnType.STRING)
                .required("revision", ColumnType.LONG)
                .primaryKey("player_id", "achievement_id")
                .createIfNotExists();
    }
}
