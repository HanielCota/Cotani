package br.com.cotani.storage.schema;

import br.com.cotani.storage.dialect.SqlDialect;
import br.com.cotani.storage.executor.QueryExecutor;
import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.security.Identifiers;
import java.util.ArrayList;
import java.util.List;

public final class TableSchema {

    private final String name;
    private final QueryExecutor executor;
    private final SqlDialect dialect;
    private final List<ColumnDefinition> columns = new ArrayList<>();

    private String cachedSql;

    TableSchema(String name, QueryExecutor executor, SqlDialect dialect) {
        this.name = Identifiers.requireValid(name, "Table name");
        this.executor = executor;
        this.dialect = dialect;
    }

    public TableSchema id(String column, ColumnType type) {
        columns.add(
                new ColumnDefinition(Identifiers.requireValid(column, "Column name"), type, 255, true, false, true));
        cachedSql = null;
        return this;
    }

    public TableSchema column(String column, ColumnType type) {
        columns.add(
                new ColumnDefinition(Identifiers.requireValid(column, "Column name"), type, 255, false, true, false));
        cachedSql = null;
        return this;
    }

    public TableSchema column(String column, ColumnType type, int length) {
        columns.add(new ColumnDefinition(
                Identifiers.requireValid(column, "Column name"), type, length, false, true, false));
        cachedSql = null;
        return this;
    }

    public TableSchema required(String column, ColumnType type) {
        columns.add(
                new ColumnDefinition(Identifiers.requireValid(column, "Column name"), type, 255, false, false, false));
        cachedSql = null;
        return this;
    }

    public TableSchema unique(String column, ColumnType type) {
        columns.add(
                new ColumnDefinition(Identifiers.requireValid(column, "Column name"), type, 255, false, false, true));
        cachedSql = null;
        return this;
    }

    public StorageFuture<Void> createIfNotExists() {
        return executor.update(sql(), binder -> {});
    }

    private String sql() {
        if (cachedSql != null) {
            return cachedSql;
        }

        StringBuilder builder =
                new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(name).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(definition(columns.get(i)));
        }
        cachedSql = builder.append(")").toString();
        return cachedSql;
    }

    private String definition(ColumnDefinition column) {
        StringBuilder builder = new StringBuilder(column.name())
                .append(" ")
                .append(dialect.type(column.type().name(), column.length()));

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
