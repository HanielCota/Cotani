package com.cotani.storage.api;

import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.StorageBackend;
import com.cotani.storage.config.StorageConfigReader;
import com.cotani.storage.dialect.DialectFactory;
import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.migration.MigrationRunner;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.provider.StorageProviderFactory;
import com.cotani.storage.query.TableQuery;
import com.cotani.storage.repository.CotaniRepository;
import com.cotani.storage.schema.Schema;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import com.cotani.storage.transaction.TransactionManager;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.util.CompletionStages;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CotaniStorage implements AutoCloseable {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final Plugin plugin;
    private final StorageBackend backend;
    private final List<Migration> migrations;
    private final List<Class<? extends CotaniRepository>> repositoryTypes;
    private final Map<Class<?>, CotaniRepository> repositories = new ConcurrentHashMap<>();
    private final ValueSerializerRegistry serializers = new ValueSerializerRegistry();
    private final StorageProvider provider;
    private final ExecutorService storageExecutor;
    private final PaperTaskScheduler scheduler;
    private final QueryExecutor executor;
    private final SqlDialect dialect;
    private final Schema schema;
    private final TransactionManager transactions;
    private final AtomicBoolean started = new AtomicBoolean();

    CotaniStorage(
            Plugin plugin,
            StorageBackend backend,
            int threads,
            boolean useVirtualThreads,
            List<Migration> migrations,
            List<Class<? extends CotaniRepository>> repositoryTypes,
            PaperTaskScheduler scheduler,
            int queryTimeoutSeconds) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.migrations = List.copyOf(Objects.requireNonNull(migrations, "migrations"));
        this.repositoryTypes = List.copyOf(Objects.requireNonNull(repositoryTypes, "repositoryTypes"));
        this.provider = new StorageProviderFactory().create(backend);
        var platformFactory =
                Thread.ofPlatform().name("cotani-storage-", 0).daemon(true).factory();
        var isSQLite = backend instanceof SQLiteBackend;
        this.storageExecutor = isSQLite
                ? Executors.newSingleThreadExecutor(platformFactory)
                : (useVirtualThreads
                        ? Executors.newThreadPerTaskExecutor(
                                Thread.ofVirtual().name("cotani-storage-vt-", 0).factory())
                        : Executors.newFixedThreadPool(threads, platformFactory));
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.dialect = new DialectFactory().create(backend);
        this.executor = new QueryExecutor(provider, storageExecutor, serializers, queryTimeoutSeconds);
        this.schema = new Schema(executor, dialect);
        this.transactions = new TransactionManager(provider, storageExecutor, serializers, queryTimeoutSeconds);
    }

    public static CotaniStorageBuilder create(Plugin plugin) {
        return new CotaniStorageBuilder(plugin);
    }

    public static CotaniStorageBuilder create(Plugin plugin, PaperTaskScheduler scheduler) {
        return new CotaniStorageBuilder(plugin).scheduler(scheduler);
    }

    public static CotaniStorageBuilder fromConfig(Plugin plugin, FileConfiguration config, String path) {
        var backend = new StorageConfigReader().read(plugin, config, path);
        return new CotaniStorageBuilder(plugin).backend(backend);
    }

    public static CotaniStorageBuilder fromConfig(
            Plugin plugin, FileConfiguration config, String path, PaperTaskScheduler scheduler) {
        return fromConfig(plugin, config, path).scheduler(scheduler);
    }

    public CompletionStage<CotaniStorage> start() {
        return startAsync();
    }

    public CompletionStage<CotaniStorage> startAsync() {
        if (!started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(this);
        }
        registerRepositories();
        return CompletableFuture.supplyAsync(
                        () -> {
                            provider.start();
                            return this;
                        },
                        storageExecutor)
                .thenCompose(storage -> runMigrations().thenApply(_ -> storage))
                .exceptionallyCompose(error -> {
                    shutdownExecutor();
                    started.set(false);
                    return CompletableFuture.failedFuture(error);
                });
    }

    public Plugin plugin() {
        return plugin;
    }

    public StorageBackend backend() {
        return backend;
    }

    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    public QueryExecutor executor() {
        return executor;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public Schema schema() {
        return schema;
    }

    public TransactionManager transactions() {
        return transactions;
    }

    public ValueSerializerRegistry serializers() {
        return serializers;
    }

    public TableQuery table(String table) {
        return new TableQuery(table, executor, dialect);
    }

    public <T extends CotaniRepository> T repository(Class<T> type) {
        return Optional.ofNullable(repositories.get(type))
                .map(type::cast)
                .orElseThrow(() -> new IllegalStateException("Repository is not registered: " + type.getName()));
    }

    @Override
    public void close() {
        shutdownExecutor();
        provider.close();
    }

    public CompletionStage<Void> closeAsync() {
        return CompletableFuture.runAsync(this::close, scheduler.asyncExecutor());
    }

    private void registerRepositories() {
        for (var type : repositoryTypes) {
            repositories.put(type, createRepository(type));
        }
    }

    private CotaniRepository createRepository(Class<? extends CotaniRepository> type) {
        try {
            var constructor = type.getConstructor(CotaniStorage.class);
            return constructor.newInstance(this);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create repository: " + type.getName(), exception);
        }
    }

    private CompletionStage<Void> runMigrations() {
        if (migrations.isEmpty()) {
            return CompletionStages.completedVoid();
        }
        var runner = new MigrationRunner(executor, schema);
        for (var migration : migrations) {
            runner.add(migration);
        }
        return runner.run();
    }

    private void shutdownExecutor() {
        storageExecutor.shutdown();
        try {
            if (!storageExecutor.awaitTermination(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                storageExecutor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            storageExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
