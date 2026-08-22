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
        var encodedHost = v.host().indexOf(':') >= 0 ? "[" + v.host() + "]" : v.host();
        var encodedDatabase =
                URLEncoder.encode(v.database(), StandardCharsets.UTF_8).replace("+", "%20");
        var base = "jdbc:mariadb://" + encodedHost + ":" + v.port() + "/" + encodedDatabase;
        var params = "?characterEncoding=utf8&useServerPrepStmts=true&connectionTimeZone=UTC";
        var ssl = v.useSsl() ? "&sslMode=verify-full" : "&sslMode=disable";

        return base + params + ssl;
    }
}
