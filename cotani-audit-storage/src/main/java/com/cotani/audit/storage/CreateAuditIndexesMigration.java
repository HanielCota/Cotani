package com.cotani.audit.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds indexes for bounded audit queries without changing the original table migration. */
public final class CreateAuditIndexesMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani audit indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("CREATE INDEX idx_cotani_audit_occurred_at ON cotani_audit_entries (occurred_at, id)")
                .thenCompose(_ -> schema.execute(
                        "CREATE INDEX idx_cotani_audit_action ON cotani_audit_entries (action, occurred_at, id)"))
                .thenCompose(
                        _ -> schema.execute(
                                "CREATE INDEX idx_cotani_audit_actor ON cotani_audit_entries (actor_type, actor_id, occurred_at, id)"))
                .thenCompose(
                        _ -> schema.execute(
                                "CREATE INDEX idx_cotani_audit_target ON cotani_audit_entries (target_type, target_id, occurred_at, id)"));
    }
}
