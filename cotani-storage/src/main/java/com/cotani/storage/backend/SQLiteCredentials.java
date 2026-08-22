package com.cotani.storage.backend;

import java.nio.file.Path;
import java.util.Objects;

public record SQLiteCredentials(Path path) implements StorageCredentials {
    public SQLiteCredentials {
        Objects.requireNonNull(path, "SQLite path is required.");
    }

    public String jdbcUrl() {
        var absolutePath = path.toAbsolutePath().toString();
        if (absolutePath.indexOf('?') >= 0 || absolutePath.indexOf(';') >= 0 || absolutePath.indexOf('#') >= 0) {
            throw new IllegalArgumentException(
                    "SQLite path must not contain JDBC delimiter characters: " + absolutePath);
        }
        return "jdbc:sqlite:" + absolutePath;
    }
}
