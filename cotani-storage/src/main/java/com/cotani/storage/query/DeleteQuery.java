package com.cotani.storage.query;

import com.cotani.storage.error.QueryError;
import com.cotani.storage.error.StorageException;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

public final class DeleteQuery {

    private final String table;
    private final QueryExecutor executor;
    private final List<Condition> conditions = new ArrayList<>();
    private boolean allowAll;

    @Nullable
    private String cachedSql;

    DeleteQuery(String table, QueryExecutor executor) {
        this.table = Identifiers.requireValid(table, "Table name");
        this.executor = executor;
    }

    public DeleteQuery where(String column, Object value) {
        conditions.add(new Condition(Identifiers.requireValid(column, "Where column"), value));
        cachedSql = null;
        return this;
    }

    public DeleteQuery all() {
        this.allowAll = true;
        return this;
    }

    public CompletionStage<Void> execute() {
        if (conditions.isEmpty() && !allowAll) {
            return CompletableFuture.failedFuture(new StorageException(new QueryError(
                    "DeleteQuery requires at least one where(...) condition; call all() to allow a full-table delete.",
                    null)));
        }
        return executor.update(sql(), this::bind);
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        var builder = new StringBuilder("DELETE FROM ").append(table);
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
        for (var condition : conditions) {
            binder.set(condition.value());
        }
    }
}
