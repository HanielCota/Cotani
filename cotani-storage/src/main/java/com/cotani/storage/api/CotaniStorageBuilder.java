package com.cotani.storage.api;

import com.cotani.storage.backend.*;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.repository.CotaniRepository;
import com.cotani.storage.security.Paths;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

public final class CotaniStorageBuilder {

    private final Plugin plugin;
    private final List<Migration> migrations = new ArrayList<>();
    private final List<Class<? extends CotaniRepository>> repositories = new ArrayList<>();

    @Nullable
    private StorageBackend backend;

    private int threads = 4;
    private boolean virtualThreads;
    private @Nullable PaperTaskScheduler scheduler;
    private Duration queryTimeout = Duration.ofSeconds(30);

    CotaniStorageBuilder(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public CotaniStorageBuilder backend(StorageBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        return this;
    }

    public CotaniStorageBuilder sqlite(String fileName) {
        var dataFolder = plugin.getDataFolder().toPath();
        var path = Paths.requireContained(dataFolder.resolve(fileName), dataFolder);
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
        var credentials = new MySqlCredentials(
                host, port, database, username, password, useSsl, MySqlCredentials.PoolSettings.defaults());
        this.backend = new MariaDbBackend(new MariaDbCredentials(credentials));
        return this;
    }

    public CotaniStorageBuilder mariaDb(String host, int port, String database, String username, String password) {
        return mariaDb(host, port, database, username, password, true);
    }

    public CotaniStorageBuilder fixedThreads(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive, got " + threads);
        }
        this.threads = threads;
        this.virtualThreads = false;
        return this;
    }

    public CotaniStorageBuilder virtualThreads() {
        this.virtualThreads = true;
        return this;
    }

    public CotaniStorageBuilder scheduler(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        return this;
    }

    public CotaniStorageBuilder queryTimeout(Duration timeout) {
        this.queryTimeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("queryTimeout must be positive, got " + timeout);
        }
        return this;
    }

    public CotaniStorageBuilder migrations(Migration... values) {
        Collections.addAll(migrations, values);
        return this;
    }

    @SafeVarargs
    @SuppressWarnings({"varargs"})
    public final CotaniStorageBuilder repositories(Class<? extends CotaniRepository>... values) {
        Collections.addAll(repositories, values);
        return this;
    }

    public CotaniStorage build() {
        Objects.requireNonNull(backend, "No backend configured; call mysql(...), mariaDb(...), or sqlite(...).");

        return new CotaniStorage(
                plugin,
                backend,
                threads,
                virtualThreads,
                List.copyOf(migrations),
                List.copyOf(repositories),
                requireScheduler(),
                (int) queryTimeout.toSeconds());
    }

    private PaperTaskScheduler requireScheduler() {
        PaperTaskScheduler resolved = scheduler;
        if (resolved == null) {
            throw new IllegalStateException("No scheduler configured; call scheduler(...) before build().");
        }
        return resolved;
    }
}
