package br.com.cotani.storage.query;

import br.com.cotani.storage.dialect.SqlDialect;
import br.com.cotani.storage.executor.QueryExecutor;
import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UpsertQuery {

    private final String table;
    private final QueryExecutor executor;
    private final SqlDialect dialect;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<String> conflicts = new ArrayList<>();
    private final List<String> updates = new ArrayList<>();
    private String cachedSql;

    UpsertQuery(String table, QueryExecutor executor, SqlDialect dialect) {
        this.table = Identifiers.requireValid(table, "Table name");
        this.executor = executor;
        this.dialect = dialect;
    }

    public UpsertQuery value(String column, Object value) {
        values.put(Identifiers.requireValid(column, "Value column"), value);
        cachedSql = null;
        return this;
    }

    public UpsertQuery conflict(String... columns) {
        for (String column : columns) {
            conflicts.add(Identifiers.requireValid(column, "Conflict column"));
        }
        cachedSql = null;
        return this;
    }

    public UpsertQuery update(String... columns) {
        for (String column : columns) {
            updates.add(Identifiers.requireValid(column, "Update column"));
        }
        cachedSql = null;
        return this;
    }

    public StorageFuture<Void> execute() {
        return executor.update(sql(), this::bind);
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        cachedSql = dialect.upsert(table, List.copyOf(values.keySet()), conflicts, updates);
        return cachedSql;
    }

    private void bind(ParameterBinder binder) throws java.sql.SQLException {
        for (Object value : values.values()) {
            binder.set(value);
        }
    }
}
