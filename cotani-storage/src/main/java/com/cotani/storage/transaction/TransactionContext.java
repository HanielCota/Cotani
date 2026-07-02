package com.cotani.storage.transaction;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.query.EntityMapper;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.SqlConsumer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public final class TransactionContext {

    private final QueryExecutor executor;

    TransactionContext(QueryExecutor executor) {
        this.executor = executor;
    }

    public CompletionStage<Void> update(String sql, SqlConsumer<ParameterBinder> binder) {
        return executor.update(sql, binder);
    }

    public <T> CompletionStage<Optional<T>> queryOne(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        return executor.queryOne(sql, binder, mapper);
    }

    public <T> CompletionStage<List<T>> queryMany(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        return executor.queryMany(sql, binder, mapper);
    }

    public CompletionStage<Boolean> exists(String sql, SqlConsumer<ParameterBinder> binder) {
        return executor.exists(sql, binder);
    }

    public CompletionStage<Void> batch(String sql, List<SqlConsumer<ParameterBinder>> binders) {
        return executor.batch(sql, binders);
    }
}
