package com.cotani.storage.api;

import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.StorageBackend;
import com.cotani.storage.config.StorageConfigReader;
import com.cotani.storage.dialect.DialectFactory;
import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.executor.AdmissionControlledExecutorService;
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
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class CotaniStorage implements AutoCloseable {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

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
    private final int queryTimeoutSeconds;
    private final Object lifecycleLock = new Object();
    private final Object resourceCloseLock = new Object();
    private final AtomicBoolean resourcesClosed = new AtomicBoolean();

    private volatile LifecycleState state = LifecycleState.NEW;
    private @Nullable CompletableFuture<CotaniStorage> startup;
    private @Nullable CompletableFuture<Void> closing;

    CotaniStorage(
            Plugin plugin,
            StorageBackend backend,
            int threads,
            boolean useVirtualThreads,
            int admissionQueueCapacity,
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
        int connectionLimit = connectionLimit(backend);
        int concurrencyLimit = useVirtualThreads ? connectionLimit : Math.min(threads, connectionLimit);
        this.storageExecutor = createStorageExecutor(
                isSQLite, useVirtualThreads, concurrencyLimit, admissionQueueCapacity, platformFactory);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.dialect = new DialectFactory().create(backend);
        Executor guardedExecutor = this::executeStorageOperation;
        this.executor = new QueryExecutor(provider, guardedExecutor, serializers, queryTimeoutSeconds);
        this.schema = new Schema(executor, dialect);
        this.transactions = new TransactionManager(provider, guardedExecutor, serializers, queryTimeoutSeconds);
    }

    private static ExecutorService createStorageExecutor(
            boolean isSQLite,
            boolean useVirtualThreads,
            int requestedConcurrency,
            int admissionQueueCapacity,
            ThreadFactory platformFactory) {
        int concurrencyLimit = isSQLite ? 1 : requestedConcurrency;
        ExecutorService workers = useVirtualThreads && !isSQLite
                ? Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("cotani-storage-vt-", 0).factory())
                : Executors.newFixedThreadPool(concurrencyLimit, platformFactory);
        return new AdmissionControlledExecutorService(workers, concurrencyLimit, admissionQueueCapacity);
    }

    private static int connectionLimit(StorageBackend backend) {
        return switch (backend) {
            case com.cotani.storage.backend.MySqlBackend mysql ->
                mysql.credentials().pool().maximumPoolSize();
            case com.cotani.storage.backend.MariaDbBackend mariaDb ->
                mariaDb.credentials().value().pool().maximumPoolSize();
            case SQLiteBackend _ -> 1;
        };
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

    public CompletionStage<CotaniStorage> startAsync() {
        synchronized (lifecycleLock) {
            if (state == LifecycleState.RUNNING) {
                return CompletableFuture.completedFuture(this);
            }
            if (state == LifecycleState.STARTING || state == LifecycleState.FAILED) {
                return Objects.requireNonNull(startup, "startup");
            }
            if (state == LifecycleState.CLOSING || state == LifecycleState.CLOSED) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("CotaniStorage cannot be started after close has begun."));
            }

            state = LifecycleState.STARTING;
            var startupPromise = new CompletableFuture<CotaniStorage>();
            startup = startupPromise;
            try {
                registerRepositories();
            } catch (Throwable failure) {
                abortFailedStartup(failure, startupPromise);
                return startupPromise;
            }

            var startupWork = CompletableFuture.supplyAsync(
                            () -> {
                                provider.start();
                                return this;
                            },
                            storageExecutor)
                    .thenCompose(storage -> runMigrations().thenApply(_ -> storage));
            var _ = startupWork.whenComplete((storage, error) -> {
                if (error != null) {
                    abortFailedStartup(error, startupPromise);
                    return;
                }
                synchronized (lifecycleLock) {
                    if (state == LifecycleState.STARTING) {
                        state = LifecycleState.RUNNING;
                    }
                }
                startupPromise.complete(storage);
            });
            return startupPromise;
        }
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

    public SqlDialect dialect() {
        return dialect;
    }

    public Schema schema() {
        return schema;
    }

    public TransactionManager transactions() {
        return transactions;
    }

    public QueryExecutor queryExecutor() {
        return executor;
    }

    public StorageExecutorStats executorStats() {
        var controlled = (AdmissionControlledExecutorService) storageExecutor;
        return new StorageExecutorStats(
                controlled.concurrencyLimit(),
                controlled.queueCapacity(),
                controlled.activeOperations(),
                controlled.queuedOperations(),
                controlled.rejectedOperations());
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
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("CotaniStorage.close() blocks; call closeAsync() off the main thread.");
        }
        synchronized (lifecycleLock) {
            if (state == LifecycleState.CLOSED) {
                return;
            }
            state = LifecycleState.CLOSING;
        }
        try {
            closeResources();
            completeClose(null);
        } catch (RuntimeException | Error failure) {
            completeClose(failure);
            throw failure;
        }
    }

    public CompletionStage<Void> closeAsync() {
        final CompletableFuture<Void> closePromise;
        final CompletionStage<?> predecessor;
        synchronized (lifecycleLock) {
            if (closing != null) {
                return closing;
            }
            closePromise = new CompletableFuture<>();
            closing = closePromise;
            predecessor = startup == null ? CompletableFuture.completedFuture(null) : startup.handle((_, _) -> null);
            state = LifecycleState.CLOSING;
        }

        var _ = predecessor.whenComplete((_, _) -> {
            try {
                scheduler.asyncExecutor().execute(() -> {
                    try {
                        closeResources();
                        completeClose(null);
                    } catch (Throwable failure) {
                        completeClose(failure);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                closePromise.completeExceptionally(rejected);
            }
        });
        return closePromise;
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
        var migrationExecutor = new QueryExecutor(provider, storageExecutor, serializers, queryTimeoutSeconds);
        var runner = new MigrationRunner(migrationExecutor, new Schema(migrationExecutor, dialect));
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

    private void executeStorageOperation(Runnable operation) {
        if (state != LifecycleState.RUNNING) {
            throw new RejectedExecutionException("CotaniStorage is not running (state=" + state + ").");
        }
        storageExecutor.execute(operation);
    }

    private void abortFailedStartup(Throwable failure, CompletableFuture<CotaniStorage> startupPromise) {
        Runnable cleanup = () -> {
            synchronized (resourceCloseLock) {
                if (resourcesClosed.compareAndSet(false, true)) {
                    storageExecutor.shutdownNow();
                    try {
                        provider.close();
                    } catch (RuntimeException | Error closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                    repositories.clear();
                }
            }
            synchronized (lifecycleLock) {
                if (state != LifecycleState.CLOSING) {
                    state = LifecycleState.FAILED;
                }
            }
            startupPromise.completeExceptionally(unwrapCompletionFailure(failure));
        };
        try {
            scheduler.asyncExecutor().execute(cleanup);
        } catch (RejectedExecutionException rejected) {
            failure.addSuppressed(rejected);
            cleanup.run();
        }
    }

    private void closeResources() {
        synchronized (resourceCloseLock) {
            if (!resourcesClosed.compareAndSet(false, true)) {
                return;
            }
            shutdownExecutor();
            provider.close();
            repositories.clear();
        }
    }

    private void completeClose(@Nullable Throwable failure) {
        CompletableFuture<Void> closePromise;
        synchronized (lifecycleLock) {
            state = LifecycleState.CLOSED;
            if (closing == null) {
                closing = new CompletableFuture<>();
            }
            closePromise = closing;
        }
        if (failure == null) {
            closePromise.complete(null);
        } else {
            closePromise.completeExceptionally(failure);
        }
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        if ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    private enum LifecycleState {
        NEW,
        STARTING,
        RUNNING,
        CLOSING,
        CLOSED,
        FAILED
    }
}
