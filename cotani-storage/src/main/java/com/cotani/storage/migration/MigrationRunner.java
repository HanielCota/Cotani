package com.cotani.storage.migration;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.schema.Schema;
import com.cotani.task.util.CompletionStages;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
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
        this.executor = Objects.requireNonNull(executor, "executor");
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    public MigrationRunner add(Migration migration) {
        Objects.requireNonNull(migration, "migration");
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
        CompletionStage<Void> seed = CompletionStages.completedVoid();
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
                        return CompletionStages.completedVoid();
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

    private void bindMigration(ParameterBinder binder, Migration migration) throws SQLException {
        binder.set(migration.version());
        binder.set(migration.description());
        binder.set(Instant.now());
    }
}
