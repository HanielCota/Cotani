package com.cotani.storage.migration;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.schema.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class MigrationRunner {

    private static final String CREATE_MIGRATIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS cotani_migrations (
                version INTEGER PRIMARY KEY,
                description TEXT NOT NULL,
                executed_at VARCHAR(64) NOT NULL
            )
            """;

    private final QueryExecutor executor;
    private final Schema schema;
    private final List<Migration> migrations = new ArrayList<>();
    private final Set<Integer> versions = new HashSet<>();

    public MigrationRunner(QueryExecutor executor, Schema schema) {
        this.executor = executor;
        this.schema = schema;
    }

    public MigrationRunner add(Migration migration) {
        if (!versions.add(migration.version())) {
            throw new IllegalArgumentException("Duplicate migration version: " + migration.version());
        }
        migrations.add(migration);
        return this;
    }

    public CompletionStage<Void> run() {
        var ordered = migrations.stream()
                .sorted(Comparator.comparingInt(Migration::version))
                .toList();
        return executor.update(CREATE_MIGRATIONS_TABLE, binder -> {}).thenCompose(_ -> runAll(ordered));
    }

    private CompletionStage<Void> runAll(List<Migration> ordered) {
        CompletionStage<Void> seed = CompletableFuture.completedStage(null);
        for (var migration : ordered) {
            seed = seed.thenCompose(_ -> runOne(migration));
        }
        return seed;
    }

    private CompletionStage<Void> runOne(Migration migration) {
        return executor.queryOne(
                        "SELECT version FROM cotani_migrations WHERE version = ?",
                        binder -> binder.set(migration.version()),
                        row -> row.getInt("version"))
                .thenCompose(existing -> {
                    if (existing.isPresent()) {
                        return CompletableFuture.completedStage(null);
                    }
                    return executor.transaction(transactional -> {
                        var transactionalSchema = new Schema(transactional, schema.dialect());
                        return migration
                                .migrate(transactionalSchema)
                                .thenCompose(_ -> markExecuted(transactional, migration));
                    });
                });
    }

    private CompletionStage<Void> markExecuted(QueryExecutor executor, Migration migration) {
        return executor.update(
                "INSERT INTO cotani_migrations (version, description, executed_at) VALUES (?, ?, ?)",
                binder -> bindMigration(binder, migration));
    }

    private void bindMigration(com.cotani.storage.query.ParameterBinder binder, Migration migration)
            throws java.sql.SQLException {
        binder.set(migration.version());
        binder.set(migration.description());
        binder.set(Instant.now());
    }
}
