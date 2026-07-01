package br.com.cotani.storage.dialect;

import br.com.cotani.storage.backend.MariaDbBackend;
import br.com.cotani.storage.backend.MySqlBackend;
import br.com.cotani.storage.backend.SQLiteBackend;
import br.com.cotani.storage.backend.StorageBackend;

public final class DialectFactory {

    public SqlDialect create(StorageBackend backend) {
        return switch (backend) {
            case MySqlBackend(var _) -> new MySqlDialect();
            case MariaDbBackend(var _) -> new MySqlDialect();
            case SQLiteBackend(var _) -> new SQLiteDialect();
        };
    }
}
