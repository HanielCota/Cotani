package br.com.cotani.storage.api;

import br.com.cotani.storage.backend.*;
import br.com.cotani.storage.migration.Migration;
import br.com.cotani.storage.repository.CotaniRepository;
import br.com.cotani.storage.security.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.plugin.Plugin;

public final class CotaniStorageBuilder {

    private final Plugin plugin;
    private StorageBackend backend;
    private int threads = 4;
    private boolean virtualThreads;
    private final List<Migration> migrations = new ArrayList<>();
    private final List<Class<? extends CotaniRepository>> repositories = new ArrayList<>();

    CotaniStorageBuilder(Plugin plugin) {
        this.plugin = plugin;
    }

    public CotaniStorageBuilder backend(StorageBackend backend) {
        this.backend = backend;
        return this;
    }

    public CotaniStorageBuilder sqlite(String fileName) {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path path = Paths.requireContained(dataFolder.resolve(fileName), dataFolder);
        this.backend = new SQLiteBackend(new SQLiteCredentials(path));
        return this;
    }

    public CotaniStorageBuilder mysql(
            String host, int port, String database, String username, String password, boolean useSsl) {
        this.backend = new MySqlBackend(new MySqlCredentials(
                host, port, database, username, password, useSsl, MySqlCredentials.PoolSettings.defaults()));
        return this;
    }

    public CotaniStorageBuilder mysql(String host, int port, String database, String username, String password) {
        return mysql(host, port, database, username, password, true);
    }

    public CotaniStorageBuilder mariaDb(
            String host, int port, String database, String username, String password, boolean useSsl) {
        MySqlCredentials credentials = new MySqlCredentials(
                host, port, database, username, password, useSsl, MySqlCredentials.PoolSettings.defaults());
        this.backend = new MariaDbBackend(new MariaDbCredentials(credentials));
        return this;
    }

    public CotaniStorageBuilder mariaDb(String host, int port, String database, String username, String password) {
        return mariaDb(host, port, database, username, password, true);
    }

    public CotaniStorageBuilder fixedThreads(int threads) {
        this.threads = threads;
        this.virtualThreads = false;
        return this;
    }

    public CotaniStorageBuilder virtualThreads() {
        this.virtualThreads = true;
        return this;
    }

    public CotaniStorageBuilder migrations(Migration... values) {
        Collections.addAll(migrations, values);
        return this;
    }

    @SafeVarargs
    @SuppressWarnings({"varargs", "unchecked"})
    public final CotaniStorageBuilder repositories(Class<? extends CotaniRepository>... values) {
        Collections.addAll(repositories, values);
        return this;
    }

    public CotaniStorage build() {
        if (backend == null) {
            return sqlite("database.db").build();
        }

        return new CotaniStorage(plugin, backend, threads, virtualThreads, migrations, repositories);
    }
}
