package com.cotani.storage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.ResultSet;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RowTest {

    @Test
    void requiredStringFailsFastOnSqlNullAndOptionalRepresentsAbsence() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("nullable")).thenReturn(null);
        var row = new Row(resultSet, new ValueSerializerRegistry());

        assertTrue(row.getStringOptional("nullable").isEmpty());
        assertThrows(NullPointerException.class, () -> row.getString("nullable"));
    }

    @Test
    void optionalTypedGettersMapPresentValues() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("created_at")).thenReturn("2026-01-01T00:00:00Z");
        when(resultSet.getLong("count")).thenReturn(42L);
        when(resultSet.wasNull()).thenReturn(false);
        var row = new Row(resultSet, new ValueSerializerRegistry());

        assertEquals(
                Instant.parse("2026-01-01T00:00:00Z"),
                row.getInstantOptional("created_at").orElseThrow());
        assertEquals(42L, row.getLongOptional("count").orElseThrow());
    }
}
