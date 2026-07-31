package com.cotani.storage.query;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;

final class JdbcInstantCodec {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private JdbcInstantCodec() {}

    static void bind(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (isSqlite(statement)) {
            // SQLite stores Cotani timestamps as ISO-8601 TEXT. Keep this representation so
            // lexical comparisons remain compatible with rows written by older versions.
            statement.setString(index, value.toString());
            return;
        }
        statement.setTimestamp(index, Timestamp.from(value), utcCalendar());
    }

    static @Nullable Instant read(ResultSet resultSet, String column) throws SQLException {
        var raw = resultSet.getObject(column);

        if (raw == null) {
            return null;
        }
        if (raw instanceof Instant instant) {
            return instant;
        }
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (raw instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (raw instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        if (raw instanceof Date date) {
            return date.toInstant();
        }
        if (raw instanceof CharSequence text) {
            return parseText(text.toString(), column);
        }

        throw new SQLException("Unsupported timestamp value in column '" + column + "': "
                + raw.getClass().getName());
    }

    private static Instant parseText(String value, String column) throws SQLException {
        try {
            return Instant.parse(value);
        } catch (RuntimeException notIsoInstant) {
            try {
                return Timestamp.valueOf(value).toLocalDateTime().toInstant(ZoneOffset.UTC);
            } catch (IllegalArgumentException notSqlTimestamp) {
                var failure = new SQLException("Invalid timestamp value in column '" + column + "': " + value);
                failure.addSuppressed(notIsoInstant);
                failure.initCause(notSqlTimestamp);
                throw failure;
            }
        }
    }

    private static boolean isSqlite(PreparedStatement statement) throws SQLException {
        var productName = statement.getConnection().getMetaData().getDatabaseProductName();
        return productName.toLowerCase(Locale.ROOT).contains("sqlite");
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(UTC);
    }
}
