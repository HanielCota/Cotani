package br.com.cotani.storage.query;

import br.com.cotani.storage.executor.QueryExecutor;
import br.com.cotani.storage.future.OptionalStorageFuture;
import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.List;

public final class SelectQuery {

    private final String table;
    private final QueryExecutor executor;
    private final List<String> columns = new ArrayList<>();
    private final List<Condition> conditions = new ArrayList<>();
    private String orderBy;
    private int limit;
    private String cachedSql;

    SelectQuery(String table, QueryExecutor executor) {
        this.table = Identifiers.requireValid(table, "Table name");
        this.executor = executor;
    }

    public SelectQuery columns(String... values) {
        for (String value : values) {
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

    public <T> OptionalStorageFuture<T> one(EntityMapper<T> mapper) {
        return executor.optionalQueryOne(sql(), this::bind, mapper);
    }

    public <T> StorageFuture<List<T>> list(EntityMapper<T> mapper) {
        return executor.queryMany(sql(), this::bind, mapper);
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        String selected = columns.isEmpty() ? "*" : String.join(", ", columns);
        StringBuilder builder =
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
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                builder.append(" AND ");
            }
            builder.append(conditions.get(i).column()).append(" = ?");
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

    private void bind(ParameterBinder binder) throws java.sql.SQLException {
        for (Condition condition : conditions) {
            binder.set(condition.value());
        }
    }
}
