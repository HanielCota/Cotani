package com.cotani.reward.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the reward claim receipts and per-player streak state tables. */
public final class CreateRewardTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani reward tables";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_rewards_claims")
                .id("claim_id", ColumnType.STRING)
                .required("player_id", ColumnType.UUID)
                .required("reward_id", ColumnType.STRING)
                .required("claimed_at", ColumnType.TIMESTAMP)
                .required("next_available_at", ColumnType.TIMESTAMP)
                .required("streak", ColumnType.INT)
                .required("total_claims", ColumnType.LONG)
                .required("grants", ColumnType.TEXT)
                .createIfNotExists()
                .thenCompose(ignored -> schema.table("cotani_rewards_states")
                        .required("player_id", ColumnType.UUID)
                        .required("reward_id", ColumnType.STRING)
                        .required("last_claim_at", ColumnType.TIMESTAMP)
                        .required("streak", ColumnType.INT)
                        .required("total_claims", ColumnType.LONG)
                        .required("revision", ColumnType.LONG)
                        .primaryKey("player_id", "reward_id")
                        .createIfNotExists());
    }
}
