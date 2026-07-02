package com.cotani.storage.dialect;

import com.cotani.storage.security.Identifiers;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class SQLiteDialect implements SqlDialect {

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    public String autoIncrement() {
        return "AUTOINCREMENT";
    }

    @Override
    public String currentTimestamp() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public String type(String logicalType, int length) {
        return switch (logicalType) {
            case "UUID", "TEXT", "TIMESTAMP", "JSON" -> "TEXT";
            case "STRING" -> "VARCHAR(" + length + ")";
            case "INT", "LONG", "BOOLEAN" -> "INTEGER";
            case "DOUBLE" -> "REAL";
            default -> throw new IllegalArgumentException("Unsupported SQLite type: " + logicalType);
        };
    }

    @Override
    public String upsert(
            String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        var columns = String.join(", ", validate(insertColumns));
        var placeholders = String.join(", ", Collections.nCopies(insertColumns.size(), "?"));
        var conflicts = String.join(", ", validate(conflictColumns));
        var validTable = Identifiers.requireValid(table, "Table name");
        if (updateColumns.isEmpty()) {
            return "INSERT INTO " + validTable + " (" + columns + ") VALUES (" + placeholders + ") ON CONFLICT("
                    + conflicts + ") DO NOTHING";
        }
        var updates = validate(updateColumns).stream()
                .map(column -> column + " = excluded." + column)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + validTable + " (" + columns + ") VALUES (" + placeholders + ") ON CONFLICT(" + conflicts
                + ") DO UPDATE SET " + updates;
    }

    private List<String> validate(List<String> identifiers) {
        return identifiers.stream()
                .map(identifier -> Identifiers.requireValid(identifier, "Column name"))
                .toList();
    }
}
