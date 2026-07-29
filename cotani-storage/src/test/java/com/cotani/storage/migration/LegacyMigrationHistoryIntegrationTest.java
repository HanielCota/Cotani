package com.cotani.storage.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyMigrationHistoryIntegrationTest {

    @Test
    void matchingLegacyHistoryIsBackfilledWithoutRerunningMigration(@TempDir Path directory) throws Exception {
        var database = directory.resolve("legacy.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TABLE cotani_migrations (
                        version INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        executed_at VARCHAR(64) NOT NULL
                    )
                    """);
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO cotani_migrations (version, description, executed_at) VALUES (?, ?, ?)")) {
                statement.setInt(1, 1);
                statement.setString(2, "legacy-create-users");
                statement.setString(3, Instant.EPOCH.toString());
                statement.executeUpdate();
            }
        }

        var executions = new AtomicInteger();
        Migration migration = new Migration() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public String description() {
                return "legacy-create-users";
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> migrate(com.cotani.storage.schema.Schema schema) {
                executions.incrementAndGet();
                return CompletableFuture.completedStage(null);
            }
        };
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        when(scheduler.asyncExecutor()).thenReturn(Runnable::run);
        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(database)))
                .scheduler(scheduler)
                .migrations(migration)
                .build();

        try {
            storage.startAsync().toCompletableFuture().join();

            assertEquals(0, executions.get());
            var historyCount = storage.queryExecutor()
                    .queryOne(
                            "SELECT COUNT(*) AS history_count FROM cotani_migrations_v2 "
                                    + "WHERE namespace = ? AND version = ?",
                            binder -> binder.string(migration.namespace()).integer(migration.version()),
                            row -> row.getInt("history_count"))
                    .toCompletableFuture()
                    .join()
                    .orElseThrow();
            assertEquals(1, historyCount);
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }
}
