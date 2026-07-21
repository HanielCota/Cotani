package com.cotani.storage.repository;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.query.TableQuery;
import com.cotani.storage.schema.Schema;
import com.cotani.storage.transaction.TransactionManager;

public abstract class CotaniRepository {

    private final CotaniStorage storage;

    protected CotaniRepository(CotaniStorage storage) {
        this.storage = storage;
    }

    protected TableQuery table(String name) {
        return storage.table(name);
    }

    protected Schema schema() {
        return storage.schema();
    }

    protected SqlDialect dialect() {
        return storage.dialect();
    }

    protected TransactionManager transactions() {
        return storage.transactions();
    }
}
