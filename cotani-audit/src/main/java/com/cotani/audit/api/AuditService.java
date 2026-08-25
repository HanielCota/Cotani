package com.cotani.audit.api;

import com.cotani.AsyncCloseable;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Records and queries immutable audit entries without accessing Bukkit objects. */
public interface AuditService extends AsyncCloseable {
    /**
     * Records an entry in submission order; failures complete the returned stage exceptionally.
     * Once a write fails, subsequent writes and queries fail with the same persistence failure;
     * recreate the service after repairing the repository.
     */
    CompletionStage<Void> recordAsync(AuditEntry entry);

    /** Queries persisted entries after pending writes have completed successfully. */
    CompletionStage<List<AuditEntry>> findAsync(AuditQuery query);
}
