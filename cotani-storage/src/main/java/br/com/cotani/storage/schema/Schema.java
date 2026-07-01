package br.com.cotani.storage.schema;

import br.com.cotani.storage.dialect.SqlDialect;
import br.com.cotani.storage.executor.QueryExecutor;

public final class Schema {

    private final QueryExecutor executor;
    private final SqlDialect dialect;

    public Schema(QueryExecutor executor, SqlDialect dialect) {
        this.executor = executor;
        this.dialect = dialect;
    }

    public TableSchema table(String name) {
        return new TableSchema(name, executor, dialect);
    }
}
