package br.com.cotani.storage.migration;

import br.com.cotani.storage.executor.QueryExecutor;
import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.query.ParameterBinder;
import br.com.cotani.storage.scheduler.CotaniScheduler;
import br.com.cotani.storage.schema.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MigrationRunner {

    private final QueryExecutor executor;
    private final CotaniScheduler scheduler;
    private final Schema schema;
    private final List<Migration> migrations = new ArrayList<>();

    public MigrationRunner(QueryExecutor executor, CotaniScheduler scheduler, Schema schema) {
        this.executor = executor;
        this.scheduler = scheduler;
        this.schema = schema;
    }

    public MigrationRunner add(Migration migration) {
        migrations.add(migration);
        return this;
    }

    public StorageFuture<Void> run() {
        List<Migration> ordered = migrations.stream()
                .sorted(Comparator.comparingInt(Migration::version))
                .toList();
        return executor.update("""
            CREATE TABLE IF NOT EXISTS cotani_migrations (
                version INTEGER PRIMARY KEY,
                description VARCHAR(255) NOT NULL,
                executed_at VARCHAR(64) NOT NULL
            )
            """, binder -> {}).flatMap(_ -> runAll(ordered));
    }

    private StorageFuture<Void> runAll(List<Migration> ordered) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> {
                    for (Migration migration : ordered) {
                        runSynchronously(migration);
                    }
                },
                executor.executor());
        return new StorageFuture<>(future, scheduler);
    }

    private void runSynchronously(Migration migration) {
        if (versionExecuted(migration.version())) {
            return;
        }

        migration.migrate(schema).raw().join();
        markExecuted(migration).raw().join();
    }

    private boolean versionExecuted(int version) {
        return executor.queryOne(
                        "SELECT version FROM cotani_migrations WHERE version = ?",
                        binder -> binder.set(version),
                        row -> row.getInt("version"))
                .raw()
                .join()
                .isPresent();
    }

    private StorageFuture<Void> markExecuted(Migration migration) {
        return executor.update(
                "INSERT INTO cotani_migrations (version, description, executed_at) VALUES (?, ?, ?)",
                binder -> bindMigration(binder, migration));
    }

    private void bindMigration(ParameterBinder binder, Migration migration) throws java.sql.SQLException {
        binder.set(migration.version());
        binder.set(migration.description());
        binder.set(Instant.now());
    }
}
