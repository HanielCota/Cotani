package com.cotani.storage.schema;

import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.executor.QueryExecutor;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class Schema {

    private final QueryExecutor executor;
    private final SqlDialect dialect;

    public Schema(QueryExecutor executor, SqlDialect dialect) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    public TableSchema table(String name) {
        return new TableSchema(name, executor, dialect);
    }

    public SqlDialect dialect() {
        return dialect;
    }

    /** Executes migration DDL that is not expressible by {@link TableSchema}. */
    public CompletionStage<Void> execute(String sql) {
        Objects.requireNonNull(sql, "sql");
        return executor.update(sql, _ -> {});
    }
}
