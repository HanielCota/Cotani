package com.cotani.storage.query;

import com.cotani.storage.error.QueryError;
import com.cotani.storage.error.StorageException;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

public final class UpdateQuery {

    private final String table;
    private final QueryExecutor executor;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<Condition> conditions = new ArrayList<>();
    private boolean allowAll;

    @Nullable
    private String cachedSql;

    UpdateQuery(String table, QueryExecutor executor) {
        this.table = Identifiers.requireValid(table, "Table name");
        this.executor = executor;
    }

    public UpdateQuery set(String column, Object value) {
        values.put(Identifiers.requireValid(column, "Set column"), value);
        cachedSql = null;
        return this;
    }

    public UpdateQuery where(String column, Object value) {
        conditions.add(new Condition(Identifiers.requireValid(column, "Where column"), value));
        cachedSql = null;
        return this;
    }

    public UpdateQuery all() {
        this.allowAll = true;
        return this;
    }

    public CompletionStage<Void> execute() {
        if (values.isEmpty()) {
            return CompletableFuture.failedFuture(new StorageException(
                    new QueryError("UpdateQuery requires at least one set(...) value before execute().", null)));
        }
        if (conditions.isEmpty() && !allowAll) {
            return CompletableFuture.failedFuture(new StorageException(new QueryError(
                    "UpdateQuery requires at least one where(...) condition; call all() to allow a full-table update.",
                    null)));
        }
        return executor.update(sql(), this::bind);
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        var builder = new StringBuilder("UPDATE ").append(table).append(" SET ");
        var iter = values.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            builder.append(entry.getKey()).append(" = ?");
            if (iter.hasNext()) {
                builder.append(", ");
            }
        }
        appendConditions(builder);
        cachedSql = builder.toString();
        return cachedSql;
    }

    private void appendConditions(StringBuilder builder) {
        if (conditions.isEmpty()) {
            return;
        }

        builder.append(" WHERE ");
        for (var i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                builder.append(" AND ");
            }
            var conditionClause = conditions.get(i).column() + " = ?";
            builder.append(conditionClause);
        }
    }

    private void bind(ParameterBinder binder) throws java.sql.SQLException {
        for (var value : values.values()) {
            binder.set(value);
        }

        for (var condition : conditions) {
            binder.set(condition.value());
        }
    }
}
