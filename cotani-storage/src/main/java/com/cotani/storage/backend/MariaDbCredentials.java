package com.cotani.storage.backend;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record MariaDbCredentials(MySqlCredentials value) implements StorageCredentials {

    public MariaDbCredentials {
        Objects.requireNonNull(value, "MariaDB credentials are required.");
    }

    public String jdbcUrl() {
        var v = value;
        var encodedHost = URLEncoder.encode(v.host(), StandardCharsets.UTF_8);
        var encodedDatabase = URLEncoder.encode(v.database(), StandardCharsets.UTF_8);
        var base = "jdbc:mariadb://" + encodedHost + ":" + v.port() + "/" + encodedDatabase;
        var params = "?characterEncoding=utf8&useServerPrepStmts=true";
        var ssl = v.useSsl() ? "&sslMode=verify-full" : "&sslMode=disable";
        return base + params + ssl;
    }
}
