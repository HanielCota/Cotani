package com.cotani.punishment.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds indexes for target and active-state punishment lookups. */
public final class CreatePunishmentIndexesMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani punishment indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute(
                        "CREATE INDEX idx_cotani_punishments_target ON cotani_punishments (target_id, punishment_type, created_at, id)")
                .thenCompose(
                        _ -> schema.execute(
                                "CREATE INDEX idx_cotani_punishments_expiry ON cotani_punishments (target_id, expires_at, revoked_at)"));
    }
}
