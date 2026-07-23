package com.cotani.storage.schema;

import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

public final class TableSchema {

    private static final String COLUMN_NAME_LABEL = "Column name";

    private final String name;
    private final QueryExecutor executor;
    private final SqlDialect dialect;
    private final List<ColumnDefinition> columns = new ArrayList<>();

    @Nullable
    private String cachedSql;

    TableSchema(String name, QueryExecutor executor, SqlDialect dialect) {
        this.name = Identifiers.requireValid(name, "Table name");
        this.executor = executor;
        this.dialect = dialect;
    }

    public TableSchema id(String column, ColumnType type) {
        var validatedName = Identifiers.requireValid(column, COLUMN_NAME_LABEL);
        columns.add(new ColumnDefinition(validatedName, type, 255, true, false, true));
        cachedSql = null;
        return this;
    }

    public TableSchema column(String column, ColumnType type) {
        var validatedName = Identifiers.requireValid(column, COLUMN_NAME_LABEL);
        columns.add(new ColumnDefinition(validatedName, type, 255, false, true, false));
        cachedSql = null;
        return this;
    }

    public TableSchema column(String column, ColumnType type, int length) {
        var validatedName = Identifiers.requireValid(column, COLUMN_NAME_LABEL);
        columns.add(new ColumnDefinition(validatedName, type, length, false, true, false));
        cachedSql = null;
        return this;
    }

    public TableSchema required(String column, ColumnType type) {
        var validatedName = Identifiers.requireValid(column, COLUMN_NAME_LABEL);
        columns.add(new ColumnDefinition(validatedName, type, 255, false, false, false));
        cachedSql = null;
        return this;
    }

    public TableSchema unique(String column, ColumnType type) {
        var validatedName = Identifiers.requireValid(column, COLUMN_NAME_LABEL);
        columns.add(new ColumnDefinition(validatedName, type, 255, false, false, true));
        cachedSql = null;
        return this;
    }

    public CompletionStage<Void> createIfNotExists() {
        return executor.update(sql(), binder -> {});
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        var builder =
                new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(name).append(" (");
        var columnCount = columns.size();
        for (var i = 0; i < columnCount; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(definition(columns.get(i)));
        }
        cachedSql = builder.append(")").toString();
        return cachedSql;
    }

    private String definition(ColumnDefinition column) {
        var typeName = column.type().name();
        var columnLength = column.length();
        var sqlType = dialect.type(typeName, columnLength);
        var builder = new StringBuilder(column.name()).append(" ").append(sqlType);

        appendPrimary(column, builder);
        appendNullable(column, builder);
        appendUnique(column, builder);
        return builder.toString();
    }

    private void appendPrimary(ColumnDefinition column, StringBuilder builder) {
        if (!column.primary()) {
            return;
        }

        builder.append(" PRIMARY KEY");
    }

    private void appendNullable(ColumnDefinition column, StringBuilder builder) {
        if (column.nullable()) {
            return;
        }

        builder.append(" NOT NULL");
    }

    private void appendUnique(ColumnDefinition column, StringBuilder builder) {
        if (!column.unique()) {
            return;
        }

        if (column.primary()) {
            return;
        }

        builder.append(" UNIQUE");
    }
}
