package br.com.cotani.storage.backend;

import java.util.Objects;

public record MariaDbCredentials(MySqlCredentials value) implements StorageCredentials {

    public MariaDbCredentials {
        Objects.requireNonNull(value, "MariaDB credentials are required.");
    }

    public String jdbcUrl() {
        return value.jdbcUrl().replace("jdbc:mysql://", "jdbc:mariadb://");
    }
}
