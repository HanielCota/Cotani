package com.cotani.audit.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.audit.api.AuditAction;
import com.cotani.audit.api.AuditActor;
import com.cotani.audit.api.AuditCursor;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditQuery;
import com.cotani.audit.api.AuditSeverity;
import com.cotani.audit.api.AuditTarget;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditStorageIntegrationTest {
    @Test
    void persistsIdempotentlyAndPaginatesWithSQLite(@TempDir Path directory) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);

        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("audit.db"))))
                .scheduler(scheduler)
                .migrations(CotaniAuditStorages.migrations().toArray(com.cotani.storage.migration.Migration[]::new))
                .build();
        try {
            storage.startAsync().toCompletableFuture().join();
            var service = CotaniAuditStorages.create(storage);
            var first = entry("first", Instant.parse("2025-01-01T00:00:00Z"));
            var second = entry("second", Instant.parse("2025-01-02T00:00:00Z"));

            service.recordAsync(first).toCompletableFuture().join();
            service.recordAsync(first).toCompletableFuture().join();
            service.recordAsync(second).toCompletableFuture().join();

            var firstPage = service.findAsync(AuditQuery.builder().limit(1).build())
                    .toCompletableFuture()
                    .join();
            var secondPage = service.findAsync(AuditQuery.builder()
                            .before(AuditCursor.after(firstPage.get(0)))
                            .limit(1)
                            .build())
                    .toCompletableFuture()
                    .join();

            assertEquals(List.of(second), firstPage);
            assertEquals(List.of(first), secondPage);
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }

    private static AuditEntry entry(String action, Instant occurredAt) {
        return new AuditEntry(
                UUID.randomUUID(),
                occurredAt,
                AuditActor.system(),
                AuditAction.of(action),
                AuditTarget.resource("server", "test"),
                AuditSeverity.INFO,
                Map.of("message", "ação|com.pontos"));
    }
}
