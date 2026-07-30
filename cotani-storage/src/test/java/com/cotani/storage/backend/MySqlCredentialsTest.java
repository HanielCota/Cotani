package com.cotani.storage.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MySqlCredentialsTest {

    @Test
    void buildsIpv6UrlWithoutFormEncodingTheHost() {
        var credentials = new MySqlCredentials(
                "2001:db8::1", 3306, "cotani data", "user", "secret", true, MySqlCredentials.PoolSettings.defaults());

        assertEquals(
                "jdbc:mysql://[2001:db8::1]:3306/cotani%20data?serverTimezone=UTC&characterEncoding=utf8"
                        + "&cachePrepStmts=true&useServerPrepStmts=true&prepStmtCacheSize=250"
                        + "&prepStmtCacheSqlLimit=2048&useSSL=true&verifyServerCertificate=true",
                credentials.jdbcUrl());
    }

    @Test
    void rejectsInvalidPoolBoundsAndDurations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlCredentials.PoolSettings(
                        1, 2, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlCredentials.PoolSettings(
                        1, 0, Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }
}
