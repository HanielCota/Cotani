package com.cotani.storage.query;

import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.executor.QueryExecutor;
import java.util.Objects;

public final class TableQuery {

    private final String table;
    private final QueryExecutor executor;
    private final SqlDialect dialect;

    public TableQuery(String table, QueryExecutor executor, SqlDialect dialect) {
        this.table = Objects.requireNonNull(table, "table");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    public SelectQuery select() {
        return new SelectQuery(table, executor);
    }

    public UpsertQuery upsert() {
        return new UpsertQuery(table, executor, dialect);
    }

    public DeleteQuery delete() {
        return new DeleteQuery(table, executor);
    }

    public UpdateQuery update() {
        return new UpdateQuery(table, executor);
    }

    public ExistsQuery exists() {
        return new ExistsQuery(table, executor);
    }
}
