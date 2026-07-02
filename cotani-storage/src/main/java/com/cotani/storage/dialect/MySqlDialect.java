package com.cotani.storage.dialect;

import com.cotani.storage.security.Identifiers;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class MySqlDialect implements SqlDialect {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String autoIncrement() {
        return "AUTO_INCREMENT";
    }

    @Override
    public String currentTimestamp() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public String type(String logicalType, int length) {
        return switch (logicalType) {
            case "UUID" -> "CHAR(36)";
            case "STRING" -> "VARCHAR(" + length + ")";
            case "TEXT" -> "TEXT";
            case "INT" -> "INT";
            case "LONG" -> "BIGINT";
            case "DOUBLE" -> "DOUBLE";
            case "BOOLEAN" -> "TINYINT(1)";
            case "TIMESTAMP" -> "TIMESTAMP";
            case "JSON" -> "JSON";
            default -> logicalType;
        };
    }

    @Override
    public String upsert(
            String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        var columns = String.join(", ", validate(insertColumns));
        var placeholders = String.join(", ", Collections.nCopies(insertColumns.size(), "?"));
        var validTable = Identifiers.requireValid(table, "Table name");
        if (updateColumns.isEmpty()) {
            return "INSERT IGNORE INTO " + validTable + " (" + columns + ") VALUES (" + placeholders + ")";
        }
        var updates = validate(updateColumns).stream()
                .map(column -> column + " = new_." + column)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + validTable + " (" + columns + ") VALUES (" + placeholders
                + ") AS new_ ON DUPLICATE KEY UPDATE " + updates;
    }

    private List<String> validate(List<String> identifiers) {
        return identifiers.stream()
                .map(identifier -> Identifiers.requireValid(identifier, "Column name"))
                .toList();
    }
}
