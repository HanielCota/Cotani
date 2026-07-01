package br.com.cotani.storage.api;

import br.com.cotani.storage.backend.StorageBackend;
import br.com.cotani.storage.config.StorageConfigReader;
import br.com.cotani.storage.dialect.DialectFactory;
import br.com.cotani.storage.dialect.SqlDialect;
import br.com.cotani.storage.executor.QueryExecutor;
import br.com.cotani.storage.executor.StorageExecutor;
import br.com.cotani.storage.migration.Migration;
import br.com.cotani.storage.migration.MigrationRunner;
import br.com.cotani.storage.provider.StorageProvider;
import br.com.cotani.storage.provider.StorageProviderFactory;
import br.com.cotani.storage.query.TableQuery;
import br.com.cotani.storage.repository.CotaniRepository;
import br.com.cotani.storage.scheduler.CotaniScheduler;
import br.com.cotani.storage.scheduler.PaperCotaniScheduler;
import br.com.cotani.storage.schema.Schema;
import br.com.cotani.storage.serializer.ValueSerializerRegistry;
import br.com.cotani.storage.transaction.TransactionManager;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class CotaniStorage implements AutoCloseable {

    private final Plugin plugin;
    private final StorageBackend backend;
    private final List<Migration> migrations;
    private final List<Class<? extends CotaniRepository>> repositoryTypes;
    private final Map<Class<?>, CotaniRepository> repositories = new ConcurrentHashMap<>();
    private final ValueSerializerRegistry serializers = new ValueSerializerRegistry();
    private final StorageProvider provider;
    private final StorageExecutor storageExecutor;
    private final CotaniScheduler scheduler;
    private final QueryExecutor executor;
    private final SqlDialect dialect;
    private final Schema schema;
    private final TransactionManager transactions;

    CotaniStorage(
            Plugin plugin,
            StorageBackend backend,
            int threads,
            boolean virtualThreads,
            List<Migration> migrations,
            List<Class<? extends CotaniRepository>> repositoryTypes) {
        this.plugin = plugin;
        this.backend = backend;
        this.migrations = migrations;
        this.repositoryTypes = repositoryTypes;
        this.provider = new StorageProviderFactory().create(backend);
        this.storageExecutor = virtualThreads ? StorageExecutor.virtualThreads() : StorageExecutor.fixed(threads);
        this.scheduler = new PaperCotaniScheduler(plugin);
        this.dialect = new DialectFactory().create(backend);
        this.executor = new QueryExecutor(provider, storageExecutor.executor(), scheduler, serializers);
        this.schema = new Schema(executor, dialect);
        this.transactions = new TransactionManager(provider, storageExecutor.executor(), scheduler);
    }

    public static CotaniStorageBuilder create(Plugin plugin) {
        return new CotaniStorageBuilder(plugin);
    }

    public static CotaniStorageBuilder fromConfig(Plugin plugin, FileConfiguration config, String path) {
        StorageBackend backend = new StorageConfigReader().read(plugin, config, path);
        return new CotaniStorageBuilder(plugin).backend(backend);
    }

    public CotaniStorage start() {
        provider.start();
        registerRepositories();
        runMigrations();
        return this;
    }

    public Plugin plugin() {
        return plugin;
    }

    public StorageBackend backend() {
        return backend;
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

    private void registerRepositories() {
        for (Class<? extends CotaniRepository> type : repositoryTypes) {
            repositories.put(type, createRepository(type));
        }
    }

    private CotaniRepository createRepository(Class<? extends CotaniRepository> type) {
        try {
            Constructor<? extends CotaniRepository> constructor = type.getConstructor(CotaniStorage.class);
            return constructor.newInstance(this);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create repository: " + type.getName(), exception);
        }
    }

    private void runMigrations() {
        MigrationRunner runner = new MigrationRunner(executor, scheduler, schema);
        for (Migration migration : migrations) {
            runner.add(migration);
        }
        runner.run().raw().join();
    }

    @Override
    public void close() {
        scheduler.close();
        provider.close();
        storageExecutor.close();
    }
}
