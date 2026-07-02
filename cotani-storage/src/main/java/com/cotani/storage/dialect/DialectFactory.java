package com.cotani.storage.dialect;

import com.cotani.storage.backend.MariaDbBackend;
import com.cotani.storage.backend.MySqlBackend;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.StorageBackend;

public final class DialectFactory {

    public SqlDialect create(StorageBackend backend) {
        return switch (backend) {
            case MySqlBackend(var _) -> new MySqlDialect();
            case MariaDbBackend(var _) -> new MariaDbDialect();
            case SQLiteBackend(var _) -> new SQLiteDialect();
        };
    }
}
