package br.com.cotani.storage.repository;

import br.com.cotani.storage.api.CotaniStorage;
import br.com.cotani.storage.future.OptionalStorageFuture;
import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.query.EntityMapper;

public abstract class CrudRepository<K, T> extends CotaniRepository implements Repository<K, T> {

    protected CrudRepository(CotaniStorage storage) {
        super(storage);
    }

    protected abstract String tableName();

    protected abstract String idColumn();

    protected abstract EntityMapper<T> mapper();

    @Override
    public OptionalStorageFuture<T> findById(K id) {
        return table(tableName()).select().where(idColumn(), id).one(mapper());
    }

    @Override
    public StorageFuture<Boolean> exists(K id) {
        return table(tableName()).exists().where(idColumn(), id).execute();
    }

    @Override
    public StorageFuture<Void> deleteById(K id) {
        return table(tableName()).delete().where(idColumn(), id).execute();
    }
}
