package br.com.cotani.storage.query;

import br.com.cotani.storage.dialect.SqlDialect;
import br.com.cotani.storage.executor.QueryExecutor;

public final class TableQuery {

    private final String table;
    private final QueryExecutor executor;
    private final SqlDialect dialect;

    public TableQuery(String table, QueryExecutor executor, SqlDialect dialect) {
        this.table = table;
        this.executor = executor;
        this.dialect = dialect;
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
