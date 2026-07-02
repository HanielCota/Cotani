package com.cotani.storage.backend;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MariaDbCredentialsTest {

    @Test
    void jdbcUrlWithSsl() {
        var mysql = new MySqlCredentials(
                "localhost",
                3306,
                "mydb",
                "user",
                "pass",
                true,
                new MySqlCredentials.PoolSettings(
                        10,
                        5,
                        java.time.Duration.ofSeconds(30),
                        java.time.Duration.ofMinutes(1),
                        java.time.Duration.ofHours(1)));
        var maria = new MariaDbCredentials(mysql);
        var url = maria.jdbcUrl();
        assertTrue(url.startsWith("jdbc:mariadb://localhost:3306/mydb"));
        assertTrue(url.contains("sslMode=verify-full"));
    }

    @Test
    void jdbcUrlWithoutSsl() {
        var mysql = new MySqlCredentials(
                "localhost",
                3306,
                "mydb",
                "user",
                "pass",
                false,
                new MySqlCredentials.PoolSettings(
                        10,
                        5,
                        java.time.Duration.ofSeconds(30),
                        java.time.Duration.ofMinutes(1),
                        java.time.Duration.ofHours(1)));
        var maria = new MariaDbCredentials(mysql);
        var url = maria.jdbcUrl();
        assertTrue(url.contains("sslMode=disable"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullMySqlCredentials() {
        assertThrows(NullPointerException.class, () -> new MariaDbCredentials(null));
    }
}
