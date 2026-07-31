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
    private static final String CREATE_LEGACY_MIGRATIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS cotani_migrations (
            version INTEGER PRIMARY KEY,
            description TEXT NOT NULL,
            executed_at VARCHAR(64) NOT NULL
        )
        """;

    private static final String CREATE_MIGRATIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS cotani_migrations_v2 (
            namespace VARCHAR(255) NOT NULL,
            version INTEGER NOT NULL,
            description TEXT NOT NULL,
            executed_at VARCHAR(64) NOT NULL,
            PRIMARY KEY (namespace, version)
        )
        """;

    private static final String SELECT_EXECUTED_VERSIONS = "SELECT namespace, version FROM cotani_migrations_v2";
    private static final String SELECT_LEGACY_MIGRATIONS = "SELECT version, description FROM cotani_migrations";

    private final QueryExecutor executor;
    private final Schema schema;
    private final List<Migration> migrations = new ArrayList<>();
    private final Set<MigrationKey> versions = new HashSet<>();

    public static MigrationRunner create(QueryExecutor executor, Schema schema) {
        return new MigrationRunner(executor, schema);
    }

    private MigrationRunner(QueryExecutor executor, Schema schema) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    public MigrationRunner add(Migration migration) {
        Objects.requireNonNull(migration, "migration");

        var key = MigrationKey.from(migration);

        if (!versions.add(key)) {
            throw new IllegalArgumentException("Duplicate migration version: " + key.namespace() + ":" + key.version());
        }

        migrations.add(migration);
        return this;
    }

    public CompletionStage<Void> run() {
        var ordered = migrations.stream()
                .sorted(Comparator.comparing(Migration::namespace).thenComparingInt(Migration::version))
                .toList();
        return executor.update(CREATE_LEGACY_MIGRATIONS_TABLE, binder -> {})
                .thenCompose(_ -> executor.update(CREATE_MIGRATIONS_TABLE, binder -> {}))
                .thenCompose(_ -> loadExecutedVersions())
                .thenCompose(executed -> backfillLegacyHistory(ordered, executed))
                .thenCompose(executed -> runAll(ordered, executed));
    }

    private CompletionStage<Set<MigrationKey>> loadExecutedVersions() {
        return executor.queryMany(
                        SELECT_EXECUTED_VERSIONS,
                        binder -> {},
                        row -> new MigrationKey(row.getString("namespace"), row.getInt("version")))
                .thenApply(list -> new HashSet<>(list));
    }

    private CompletionStage<Set<MigrationKey>> backfillLegacyHistory(
            List<Migration> ordered, Set<MigrationKey> executed) {
        return executor.queryMany(
                        SELECT_LEGACY_MIGRATIONS,
                        binder -> {},
                        row -> new LegacyMigration(row.getInt("version"), row.getString("description")))
                .thenCompose(legacyRows -> {
                    var legacy = new HashSet<>(legacyRows);
                    CompletionStage<Void> seed = CompletionStages.completedVoid();

                    for (var migration : ordered) {
                        var key = MigrationKey.from(migration);
                        var legacyKey = LegacyMigration.from(migration);

                        if (executed.contains(key) || !legacy.contains(legacyKey)) {
                            continue;
                        }
                        seed = seed.thenCompose(_ -> markExecuted(executor, migration));
                        executed.add(key);
                    }
                    return seed.thenApply(_ -> executed);
                });
    }

    private CompletionStage<Void> runAll(List<Migration> ordered, Set<MigrationKey> executed) {
        CompletionStage<Void> seed = CompletionStages.completedVoid();

        for (var migration : ordered) {
            if (executed.contains(MigrationKey.from(migration))) {
                continue;
            }
            seed = seed.thenCompose(_ -> runOne(migration));
        }
        return seed;
    }

    private CompletionStage<Void> runOne(Migration migration) {
        return executor.transaction(transactional -> {
            var transactionalSchema = new Schema(transactional, schema.dialect());
            return migration.migrate(transactionalSchema).thenCompose(_ -> markExecuted(transactional, migration));
        });
    }

    private CompletionStage<Void> markExecuted(QueryExecutor executor, Migration migration) {
        return executor.update(
                "INSERT INTO cotani_migrations_v2 (namespace, version, description, executed_at) VALUES (?, ?, ?, ?)",
                binder -> bindMigration(binder, migration));
    }

    private void bindMigration(ParameterBinder binder, Migration migration) throws SQLException {
        binder.set(migration.namespace());
        binder.set(migration.version());
        binder.set(migration.description());
        binder.set(Instant.now());
    }

    private record MigrationKey(String namespace, int version) {
        private MigrationKey {
            Objects.requireNonNull(namespace, "namespace");

            if (namespace.isBlank()) {
                throw new IllegalArgumentException("Migration namespace must not be blank.");
            }
            if (namespace.length() > 255) {
                throw new IllegalArgumentException("Migration namespace exceeds 255 characters.");
            }
        }

        private static MigrationKey from(Migration migration) {
            return new MigrationKey(migration.namespace(), migration.version());
        }
    }

    private record LegacyMigration(int version, String description) {
        private LegacyMigration {
            Objects.requireNonNull(description, "description");
        }

        private static LegacyMigration from(Migration migration) {
            return new LegacyMigration(migration.version(), migration.description());
        }
    }
}
