package com.cotani.reward.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds durable settlement acknowledgements to existing reward claim tables. */
public final class MigrateRewardSettlementMigration implements Migration {
    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Add Cotani reward settlement state";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("ALTER TABLE cotani_rewards_claims ADD COLUMN settled BOOLEAN NOT NULL DEFAULT FALSE");
    }
}
