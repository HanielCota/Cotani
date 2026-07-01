package br.com.cotani.storage.dialect;

import br.com.cotani.storage.security.Identifiers;
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
        String columns = String.join(", ", validate(insertColumns));
        String placeholders = String.join(", ", Collections.nCopies(insertColumns.size(), "?"));
        String updates = validate(updateColumns).stream()
                .map(column -> column + " = new_." + column)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + Identifiers.requireValid(table, "Table name") + " (" + columns + ") VALUES ("
                + placeholders + ") AS new_" + " ON DUPLICATE KEY UPDATE " + updates;
    }

    private List<String> validate(List<String> identifiers) {
        return identifiers.stream()
                .map(identifier -> Identifiers.requireValid(identifier, "Column name"))
                .toList();
    }
}
