package com.cotani.reward.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds indexes used by claim retention and player history queries. */
public final class CreateRewardIndexesMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani reward indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("CREATE INDEX IF NOT EXISTS cotani_rewards_claim_lookup_idx "
                        + "ON cotani_rewards_claims (player_id, reward_id, claimed_at)")
                .thenCompose(ignored -> schema.execute("CREATE INDEX IF NOT EXISTS cotani_rewards_claim_expiry_idx "
                        + "ON cotani_rewards_claims (claimed_at)"));
    }
}
