package com.cotani.storage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import org.junit.jupiter.api.Test;

class JdbcInstantCodecTest {

    private static final Instant SAMPLE = Instant.parse("2026-07-29T12:34:56.123456Z");

    @Test
    void bindsNativeTimestampForMysqlInUtc() throws SQLException {
        var statement = statementFor("MySQL");

        new ParameterBinder(statement, mock(ValueSerializerRegistry.class)).instant(SAMPLE);

        verify(statement).setTimestamp(eq(1), eq(Timestamp.from(SAMPLE)), any(Calendar.class));
    }

    @Test
    void keepsIsoTextRepresentationForSqlite() throws SQLException {
        var statement = statementFor("SQLite");

        new ParameterBinder(statement, mock(ValueSerializerRegistry.class)).set(SAMPLE);

        verify(statement).setString(1, SAMPLE.toString());
    }

    @Test
    void readsNativeJdbcTimestamp() throws SQLException {
        var resultSet = mock(ResultSet.class);
        when(resultSet.getObject("created_at")).thenReturn(Timestamp.from(SAMPLE));

        var row = new Row(resultSet, mock(ValueSerializerRegistry.class));

        assertEquals(SAMPLE, row.getInstantOptional("created_at").orElseThrow());
    }

    @Test
    void readsIsoAndSqlTimestampText() throws SQLException {
        var resultSet = mock(ResultSet.class);
        var row = new Row(resultSet, mock(ValueSerializerRegistry.class));

        when(resultSet.getObject("created_at")).thenReturn(SAMPLE.toString());
        assertEquals(SAMPLE, row.getInstantOptional("created_at").orElseThrow());

        when(resultSet.getObject("created_at")).thenReturn("2026-07-29 12:34:56.123456");
        assertEquals(SAMPLE, row.getInstantOptional("created_at").orElseThrow());
    }

    @Test
    void rejectsInvalidTimestampWithSqlContext() throws SQLException {
        var resultSet = mock(ResultSet.class);
        when(resultSet.getObject("created_at")).thenReturn("not-a-timestamp");
        var row = new Row(resultSet, mock(ValueSerializerRegistry.class));

        var error = assertThrows(SQLException.class, () -> row.getInstantOptional("created_at"));

        assertEquals("Invalid timestamp value in column 'created_at': not-a-timestamp", error.getMessage());
    }

    private static PreparedStatement statementFor(String databaseProduct) throws SQLException {
        var statement = mock(PreparedStatement.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(databaseProduct);
        return statement;
    }
}
