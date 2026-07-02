package com.cotani.storage.query;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

public final class ExistsQuery {

    private final String table;
    private final QueryExecutor executor;
    private final List<Condition> conditions = new ArrayList<>();

    @Nullable
    private String cachedSql;

    ExistsQuery(String table, QueryExecutor executor) {
        this.table = Identifiers.requireValid(table, "Table name");
        this.executor = executor;
    }

    public ExistsQuery where(String column, Object value) {
        conditions.add(new Condition(Identifiers.requireValid(column, "Where column"), value));
        cachedSql = null;
        return this;
    }

    public CompletionStage<Boolean> execute() {
        return executor.exists(sql(), this::bind);
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        var builder = new StringBuilder("SELECT 1 FROM ").append(table);
        appendConditions(builder);
        builder.append(" LIMIT 1");
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
