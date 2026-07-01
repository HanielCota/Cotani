package br.com.cotani.storage.config;

import br.com.cotani.storage.backend.*;
import br.com.cotani.storage.security.Paths;
import br.com.cotani.storage.type.StorageKind;
import java.nio.file.Path;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class StorageConfigReader {

    public StorageBackend read(Plugin plugin, FileConfiguration config, String path) {
        String typeName = config.getString(path + ".type", "SQLITE");
        StorageKind kind = StorageKind.valueOf(typeName.toUpperCase(Locale.ROOT));
        return switch (kind) {
            case SQLITE -> sqlite(plugin, config, path);
            case MYSQL -> mysql(config, path);
            case MARIADB -> maria(config, path);
        };
    }

    private StorageBackend sqlite(Plugin plugin, FileConfiguration config, String path) {
        String file = config.getString(path + ".sqlite.file", "database.db");
        Path dataFolder = plugin.getDataFolder().toPath();
        Path database = Paths.requireContained(dataFolder.resolve(file), dataFolder);
        return new SQLiteBackend(new SQLiteCredentials(database));
    }

    private StorageBackend mysql(FileConfiguration config, String path) {
        return new MySqlBackend(readMysql(config, path));
    }

    private StorageBackend maria(FileConfiguration config, String path) {
        return new MariaDbBackend(new MariaDbCredentials(readMysql(config, path)));
    }

    private MySqlCredentials readMysql(FileConfiguration config, String path) {
        String username = config.getString(path + ".mysql.username");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("MySQL username is required at " + path + ".mysql.username");
        }

        String password = config.getString(path + ".mysql.password");
        if (password == null) {
            throw new IllegalArgumentException("MySQL password is required at " + path + ".mysql.password");
        }

        return new MySqlCredentials(
                config.getString(path + ".mysql.host", "localhost"),
                config.getInt(path + ".mysql.port", 3306),
                config.getString(path + ".mysql.database", "minecraft"),
                username,
                password,
                config.getBoolean(path + ".mysql.use-ssl", true),
                new MySqlCredentials.PoolSettings(
                        config.getInt(path + ".mysql.pool-size", 10),
                        config.getInt(path + ".mysql.minimum-idle", 2),
                        java.time.Duration.ofMillis(config.getLong(path + ".mysql.connection-timeout", 10_000L)),
                        java.time.Duration.ofMillis(config.getLong(path + ".mysql.idle-timeout", 60_000L)),
                        java.time.Duration.ofMillis(config.getLong(path + ".mysql.max-lifetime", 1_800_000L))));
    }
}
