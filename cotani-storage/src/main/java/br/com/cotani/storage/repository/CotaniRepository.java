package br.com.cotani.storage.repository;

import br.com.cotani.storage.api.CotaniStorage;
import br.com.cotani.storage.dialect.SqlDialect;
import br.com.cotani.storage.executor.QueryExecutor;
import br.com.cotani.storage.query.TableQuery;
import br.com.cotani.storage.schema.Schema;
import br.com.cotani.storage.transaction.TransactionManager;

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

    protected QueryExecutor executor() {
        return storage.executor();
    }

    protected SqlDialect dialect() {
        return storage.dialect();
    }

    protected TransactionManager transactions() {
        return storage.transactions();
    }
}
