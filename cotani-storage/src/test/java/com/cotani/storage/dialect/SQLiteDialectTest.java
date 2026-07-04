package com.cotani.storage.dialect;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SQLiteDialectTest {

    private final SQLiteDialect dialect = new SQLiteDialect();

    @Test
    void upsertWithUpdates() {
        var sql = dialect.upsert("players", List.of("id", "name"), List.of("id"), List.of("name"));
        assertTrue(sql.contains("ON CONFLICT(id) DO UPDATE SET"));
        assertTrue(sql.contains("name = excluded.name"));
    }

    @Test
    void upsertWithoutUpdates() {
        var sql = dialect.upsert("players", List.of("id", "name"), List.of("id"), List.of());
        assertTrue(sql.contains("ON CONFLICT(id) DO NOTHING"));
    }

    @Test
    void typeMapsCorrectly() {
        assertEquals("TEXT", dialect.type("UUID", 0));
        assertEquals("INTEGER", dialect.type("INT", 0));
        assertEquals("INTEGER", dialect.type("LONG", 0));
        assertEquals("INTEGER", dialect.type("BOOLEAN", 0));
        assertEquals("REAL", dialect.type("DOUBLE", 0));
        assertEquals("VARCHAR(255)", dialect.type("STRING", 255));
        assertEquals("TEXT", dialect.type("TEXT", 0));
    }

    @Test
    void unknownTypeThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> dialect.type("UNKNOWN", 0));
    }

    @Test
    void name() {
        assertEquals("sqlite", dialect.name());
    }
}
