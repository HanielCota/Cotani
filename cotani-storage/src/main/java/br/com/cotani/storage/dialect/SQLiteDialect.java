package br.com.cotani.storage.dialect;

import br.com.cotani.storage.security.Identifiers;
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
            case "UUID" -> "TEXT";
            case "STRING" -> "VARCHAR(" + length + ")";
            case "TEXT" -> "TEXT";
            case "INT" -> "INTEGER";
            case "LONG" -> "INTEGER";
            case "DOUBLE" -> "REAL";
            case "BOOLEAN" -> "INTEGER";
            case "TIMESTAMP" -> "TEXT";
            case "JSON" -> "TEXT";
            default -> logicalType;
        };
    }

    @Override
    public String upsert(
            String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns) {
        String columns = String.join(", ", validate(insertColumns));
        String placeholders = String.join(", ", Collections.nCopies(insertColumns.size(), "?"));
        String conflicts = String.join(", ", validate(conflictColumns));
        String updates = validate(updateColumns).stream()
                .map(column -> column + " = excluded." + column)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + Identifiers.requireValid(table, "Table name") + " (" + columns + ") VALUES ("
                + placeholders + ") ON CONFLICT(" + conflicts + ") DO UPDATE SET " + updates;
    }

    private List<String> validate(List<String> identifiers) {
        return identifiers.stream()
                .map(identifier -> Identifiers.requireValid(identifier, "Column name"))
                .toList();
    }
}
