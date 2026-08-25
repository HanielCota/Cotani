package com.cotani.cleanup.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** SPI that owns world scanning and region-safe entity removal. */
public interface CleanupExecutor {
    /**
     * Scans loaded areas and returns immutable snapshots; it must not remove anything.
     * Implementations must perform Bukkit/Paper access on the owning server thread.
     */
    CompletionStage<CleanupScan> scanAsync(CleanupPolicy policy);

    /**
     * Revalidates and removes the supplied snapshots on their owning entity or region threads.
     * A missing or changed entity should be reported as skipped instead of failing the whole run.
     */
    CompletionStage<CleanupRemovalResult> removeAsync(CleanupPolicy policy, List<CleanupEntitySnapshot> candidates);
}
