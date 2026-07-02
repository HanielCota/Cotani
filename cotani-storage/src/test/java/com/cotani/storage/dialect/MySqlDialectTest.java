package com.cotani.storage.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MySqlDialectTest {

    private final MySqlDialect dialect = new MySqlDialect();

    @Test
    void upsertWithUpdates() {
        var sql = dialect.upsert("players", List.of("id", "name"), List.of("id"), List.of("name"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("name = new_.name"));
    }

    @Test
    void upsertWithoutUpdates() {
        var sql = dialect.upsert("players", List.of("id", "name"), List.of("id"), List.of());
        assertTrue(sql.startsWith("INSERT IGNORE INTO"));
    }

    @Test
    void typeMapsCorrectly() {
        assertEquals("CHAR(36)", dialect.type("UUID", 0));
        assertEquals("INT", dialect.type("INT", 0));
        assertEquals("BIGINT", dialect.type("LONG", 0));
        assertEquals("TINYINT(1)", dialect.type("BOOLEAN", 0));
        assertEquals("DOUBLE", dialect.type("DOUBLE", 0));
        assertEquals("VARCHAR(255)", dialect.type("STRING", 255));
        assertEquals("JSON", dialect.type("JSON", 0));
    }

    @Test
    void name() {
        assertEquals("mysql", dialect.name());
    }
}
