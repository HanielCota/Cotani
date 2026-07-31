package com.cotani.storage.schema;

import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

public final class TableSchema {
    private static final String COLUMN_NAME_LABEL = "Column name";

    private final String name;
    private final QueryExecutor executor;
    private final SqlDialect dialect;
    private final List<ColumnDefinition> columns = new ArrayList<>();
    private final List<String> compositePrimaryKey = new ArrayList<>();

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

    /**
     * Declares a composite primary key. Column names must already be present via
     * {@link #required}, {@link #column}, {@link #unique} or {@link #id}.
     *
     * <p>When a composite key is set, per-column {@code PRIMARY KEY} markers from {@link #id}
     * are omitted in favor of a single table-level constraint.
     */
    public TableSchema primaryKey(String... columns) {
        Objects.requireNonNull(columns, "columns");

        if (columns.length == 0) {
            throw new IllegalArgumentException("primary key must contain at least one column");
        }

        compositePrimaryKey.clear();
        for (String column : columns) {
            var validatedName = Identifiers.requireValid(column, COLUMN_NAME_LABEL);
            compositePrimaryKey.add(validatedName);
        }
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

        boolean useCompositePrimaryKey = !compositePrimaryKey.isEmpty();
        var builder =
                new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(name).append(" (");
        var columnCount = columns.size();

        for (var i = 0; i < columnCount; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(definition(columns.get(i), useCompositePrimaryKey));
        }
        if (useCompositePrimaryKey) {
            builder.append(", PRIMARY KEY (");
            for (var i = 0; i < compositePrimaryKey.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(compositePrimaryKey.get(i));
            }
            builder.append(')');
        }
        cachedSql = builder.append(")").toString();

        return cachedSql;
    }

    private String definition(ColumnDefinition column, boolean useCompositePrimaryKey) {
        var typeName = column.type().name();
        var columnLength = column.length();
        var sqlType = dialect.type(typeName, columnLength);
        var builder = new StringBuilder(column.name()).append(" ").append(sqlType);

        appendPrimary(column, builder, useCompositePrimaryKey);
        appendNullable(column, builder);
        appendUnique(column, builder, useCompositePrimaryKey);

        return builder.toString();
    }

    private void appendPrimary(ColumnDefinition column, StringBuilder builder, boolean useCompositePrimaryKey) {
        if (!column.primary() || useCompositePrimaryKey) {
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

    private void appendUnique(ColumnDefinition column, StringBuilder builder, boolean useCompositePrimaryKey) {
        if (!column.unique()) {
            return;
        }

        if (column.primary() && !useCompositePrimaryKey) {
            return;
        }

        builder.append(" UNIQUE");
    }
}
