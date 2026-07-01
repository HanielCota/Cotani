package br.com.cotani.storage.backend;

import java.nio.file.Path;
import java.util.Objects;

public record SQLiteCredentials(Path path) implements StorageCredentials {

    public SQLiteCredentials {
        Objects.requireNonNull(path, "SQLite path is required.");
    }

    public String jdbcUrl() {
        return "jdbc:sqlite:" + path.toAbsolutePath();
    }
}
