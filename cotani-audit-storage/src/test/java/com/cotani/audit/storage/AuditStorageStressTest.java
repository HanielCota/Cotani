package com.cotani.audit.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.audit.api.AuditAction;
import com.cotani.audit.api.AuditActor;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditQuery;
import com.cotani.audit.api.AuditSeverity;
import com.cotani.audit.api.AuditTarget;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.testkit.StressTestSupport;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("stress")
class AuditStorageStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void persistsOneThousandConcurrentEntriesAndOneThousandIdempotentRetries(@TempDir Path directory) {
        var storage = createStorage(directory.resolve("audit-stress.db"));
        try {
            storage.startAsync().toCompletableFuture().join();
            var service = CotaniAuditStorages.create(storage);
            var entries = StressTestSupport.concurrent(
                    "audit-storage",
                    "sqlite-concurrent-append",
                    StressTestSupport.MINIMUM_ITERATIONS,
                    32,
                    TIMEOUT,
                    index -> {
                        var entry = entry(index);
                        return service.recordAsync(entry).thenApply(_ -> entry);
                    });

            var retriedEntry = entries.getFirst();
            StressTestSupport.concurrent(
                    "audit-storage",
                    "sqlite-idempotent-retry",
                    StressTestSupport.MINIMUM_ITERATIONS,
                    32,
                    TIMEOUT,
                    _ -> service.recordAsync(retriedEntry));

            var persisted = service.findAsync(AuditQuery.builder()
                            .limit(StressTestSupport.MINIMUM_ITERATIONS)
                            .build())
                    .toCompletableFuture()
                    .join();
            assertEquals(StressTestSupport.MINIMUM_ITERATIONS, persisted.size());
            assertEquals(
                    StressTestSupport.MINIMUM_ITERATIONS,
                    persisted.stream().map(AuditEntry::id).distinct().count());
            assertEquals(entry(StressTestSupport.MINIMUM_ITERATIONS - 1), persisted.getFirst());
            assertEquals(retriedEntry, persisted.getLast());
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }

    private static CotaniStorage createStorage(Path database) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);

        return CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(database)))
                .scheduler(scheduler)
                .migrations(CotaniAuditStorages.migrations().toArray(com.cotani.storage.migration.Migration[]::new))
                .build();
    }

    private static AuditEntry entry(int index) {
        var playerId = new UUID(0x6175646974L, index + 1L);
        return new AuditEntry(
                new UUID(0x656e747279L, index + 1L),
                BASE_TIME.plusSeconds(index),
                AuditActor.player(playerId),
                AuditAction.of("stress.action." + index % 7),
                AuditTarget.resource("player", playerId.toString()),
                severity(index),
                Map.of("iteration", Integer.toString(index), "message", "ação|audit." + index));
    }

    private static AuditSeverity severity(int index) {
        return switch (index % 3) {
            case 0 -> AuditSeverity.INFO;
            case 1 -> AuditSeverity.WARNING;
            default -> AuditSeverity.CRITICAL;
        };
    }
}
