package com.cotani.storage.schema;

import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.executor.QueryExecutor;
import java.util.Objects;

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
}
