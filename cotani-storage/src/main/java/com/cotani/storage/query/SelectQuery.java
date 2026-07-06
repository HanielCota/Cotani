package com.cotani.storage.query;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.security.Identifiers;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

public final class SelectQuery {

    private final String table;
    private final QueryExecutor executor;
    private final List<String> columns = new ArrayList<>();
    private final List<Condition> conditions = new ArrayList<>();

    @Nullable
    private String orderBy;

    private int limit;

    @Nullable
    private String cachedSql;

    SelectQuery(String table, QueryExecutor executor) {
        this.table = Identifiers.requireValid(table, "Table name");
        this.executor = executor;
    }

    public SelectQuery columns(String... values) {
        for (var value : values) {
            columns.add(Identifiers.requireValid(value, "Column name"));
        }
        cachedSql = null;
        return this;
    }

    public SelectQuery where(String column, Object value) {
        conditions.add(new Condition(Identifiers.requireValid(column, "Where column"), value));
        cachedSql = null;
        return this;
    }

    public SelectQuery orderByDesc(String column) {
        this.orderBy = Identifiers.requireValid(column, "Order by column") + " DESC";
        cachedSql = null;
        return this;
    }

    public SelectQuery orderByAsc(String column) {
        this.orderBy = Identifiers.requireValid(column, "Order by column") + " ASC";
        cachedSql = null;
        return this;
    }

    public SelectQuery limit(int value) {
        this.limit = value;
        cachedSql = null;
        return this;
    }

    public <T> CompletionStage<Optional<T>> one(EntityMapper<T> mapper) {
        return executor.queryOne(sql(), this::bind, mapper);
    }

    public <T> CompletionStage<List<T>> list(EntityMapper<T> mapper) {
        return executor.queryMany(sql(), this::bind, mapper);
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        var selected = columns.isEmpty() ? "*" : String.join(", ", columns);
        var builder =
                new StringBuilder("SELECT ").append(selected).append(" FROM ").append(table);
        appendConditions(builder);
        appendOrder(builder);
        appendLimit(builder);
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

    private void appendOrder(StringBuilder builder) {
        if (orderBy == null) {
            return;
        }

        builder.append(" ORDER BY ").append(orderBy);
    }

    private void appendLimit(StringBuilder builder) {
        if (limit <= 0) {
            return;
        }

        builder.append(" LIMIT ").append(limit);
    }

    private void bind(ParameterBinder binder) throws SQLException {
        for (var condition : conditions) {
            binder.set(condition.value());
        }
    }
}
