package com.cotani.storage.config;

import com.cotani.storage.backend.*;
import com.cotani.storage.security.Paths;
import com.cotani.storage.type.StorageKind;
import java.time.Duration;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class StorageConfigReader {

    public StorageBackend read(Plugin plugin, FileConfiguration config, String path) {
        var typeName = config.getString(path + ".type", "SQLITE");
        var kind = StorageKind.valueOf(typeName.toUpperCase(Locale.ROOT));
        return switch (kind) {
            case SQLITE -> sqlite(plugin, config, path);
            case MYSQL -> mysql(config, path);
            case MARIADB -> maria(config, path);
        };
    }

    private StorageBackend sqlite(Plugin plugin, FileConfiguration config, String path) {
        var file = config.getString(path + ".sqlite.file", "database.db");
        var dataFolder = plugin.getDataFolder().toPath();
        var database = Paths.requireContained(dataFolder.resolve(file), dataFolder);
        return new SQLiteBackend(new SQLiteCredentials(database));
    }

    private StorageBackend mysql(FileConfiguration config, String path) {
        return new MySqlBackend(readMysql(config, path));
    }

    private StorageBackend maria(FileConfiguration config, String path) {
        return new MariaDbBackend(new MariaDbCredentials(readMysql(config, path)));
    }

    private MySqlCredentials readMysql(FileConfiguration config, String path) {
        var prefix = path + ".mysql.";

        var username = config.getString(prefix + "username");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("MySQL username is required at " + prefix + "username");
        }

        var password = config.getString(prefix + "password");
        if (password == null) {
            throw new IllegalArgumentException("MySQL password is required at " + prefix + "password");
        }

        var host = config.getString(prefix + "host", "localhost");
        var port = config.getInt(prefix + "port", 3306);
        var database = config.getString(prefix + "database", "minecraft");
        var useSsl = config.getBoolean(prefix + "use-ssl", true);
        var poolSize = config.getInt(prefix + "pool-size", 10);
        var minimumIdle = config.getInt(prefix + "minimum-idle", 2);
        var connectionTimeout = Duration.ofMillis(config.getLong(prefix + "connection-timeout", 10_000L));
        var idleTimeout = Duration.ofMillis(config.getLong(prefix + "idle-timeout", 60_000L));
        var maxLifetime = Duration.ofMillis(config.getLong(prefix + "max-lifetime", 1_800_000L));

        return new MySqlCredentials(
                host,
                port,
                database,
                username,
                password,
                useSsl,
                new MySqlCredentials.PoolSettings(poolSize, minimumIdle, connectionTimeout, idleTimeout, maxLifetime));
    }
}
