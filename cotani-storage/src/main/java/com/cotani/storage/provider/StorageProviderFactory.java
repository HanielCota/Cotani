package com.cotani.storage.provider;

import com.cotani.storage.backend.MariaDbBackend;
import com.cotani.storage.backend.MySqlBackend;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.StorageBackend;

public final class StorageProviderFactory {

    public StorageProvider create(StorageBackend backend) {
        return switch (backend) {
            case MySqlBackend(var mysqlCredentials) ->
                new HikariStorageProvider(mysqlCredentials.jdbcUrl(), mysqlCredentials);
            case MariaDbBackend(var mariaDbCredentials) ->
                new HikariStorageProvider(mariaDbCredentials.jdbcUrl(), mariaDbCredentials.value());
            case SQLiteBackend(var sqliteCredentials) -> new SQLiteStorageProvider(sqliteCredentials);
        };
    }
}
