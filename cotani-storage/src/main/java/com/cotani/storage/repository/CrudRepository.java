package com.cotani.storage.repository;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.query.EntityMapper;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public abstract class CrudRepository<K, T> extends CotaniRepository implements Repository<K, T> {

    protected CrudRepository(CotaniStorage storage) {
        super(storage);
    }

    protected abstract String tableName();

    protected abstract String idColumn();

    protected abstract EntityMapper<T> mapper();

    @Override
    public CompletionStage<Optional<T>> findById(K id) {
        return table(tableName()).select().where(idColumn(), id).one(mapper());
    }

    @Override
    public CompletionStage<Boolean> exists(K id) {
        return table(tableName()).exists().where(idColumn(), id).execute();
    }

    @Override
    public CompletionStage<Void> deleteById(K id) {
        return table(tableName()).delete().where(idColumn(), id).execute();
    }
}
