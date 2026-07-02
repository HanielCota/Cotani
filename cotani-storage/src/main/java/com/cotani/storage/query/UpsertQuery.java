package com.cotani.storage.query;

import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

public final class UpsertQuery {

    private final String table;
    private final QueryExecutor executor;
    private final SqlDialect dialect;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<String> conflicts = new ArrayList<>();
    private final List<String> updates = new ArrayList<>();

    @Nullable
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
        for (var column : columns) {
            conflicts.add(Identifiers.requireValid(column, "Conflict column"));
        }
        cachedSql = null;
        return this;
    }

    public UpsertQuery update(String... columns) {
        for (var column : columns) {
            updates.add(Identifiers.requireValid(column, "Update column"));
        }
        cachedSql = null;
        return this;
    }

    public CompletionStage<Void> execute() {
        validate();
        return executor.update(sql(), this::bind);
    }

    private void validate() {
        var columns = values.keySet();
        for (var column : conflicts) {
            if (!columns.contains(column)) {
                throw new IllegalArgumentException("Conflict column '" + column + "' is not present in values.");
            }
        }
        for (var column : updates) {
            if (!columns.contains(column)) {
                throw new IllegalArgumentException("Update column '" + column + "' is not present in values.");
            }
        }
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        cachedSql = dialect.upsert(table, List.copyOf(values.keySet()), conflicts, updates);
        return cachedSql;
    }

    private void bind(ParameterBinder binder) throws java.sql.SQLException {
        for (var value : values.values()) {
            binder.set(value);
        }
    }
}
