package com.cotani.storage.query;

import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

public final class Row {

    private final ResultSet resultSet;
    private final ValueSerializerRegistry serializers;

    public Row(ResultSet resultSet, ValueSerializerRegistry serializers) {
        this.resultSet = Objects.requireNonNull(resultSet, "resultSet");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    public String getString(String column) throws SQLException {
        return resultSet.getString(column);
    }

    public int getInt(String column) throws SQLException {
        return resultSet.getInt(column);
    }

    public long getLong(String column) throws SQLException {
        return resultSet.getLong(column);
    }

    public double getDouble(String column) throws SQLException {
        return resultSet.getDouble(column);
    }

    public boolean getBoolean(String column) throws SQLException {
        return resultSet.getBoolean(column);
    }

    public @Nullable Integer getIntOrNull(String column) throws SQLException {
        int value = resultSet.getInt(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    public @Nullable Long getLongOrNull(String column) throws SQLException {
        long value = resultSet.getLong(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    public @Nullable Double getDoubleOrNull(String column) throws SQLException {
        double value = resultSet.getDouble(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    public @Nullable Boolean getBooleanOrNull(String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    public @Nullable UUID getUuid(String column) throws SQLException {
        var raw = resultSet.getString(column);
        if (raw == null) {
            return null;
        }
        return UUID.fromString(raw);
    }

    public @Nullable Instant getInstant(String column) throws SQLException {
        var raw = resultSet.getString(column);
        if (raw == null) {
            return null;
        }
        return Instant.parse(raw);
    }

    public @Nullable Duration getDuration(String column) throws SQLException {
        long value = resultSet.getLong(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return Duration.ofMillis(value);
    }

    public <E extends Enum<E>> @Nullable E getEnum(String column, Class<E> enumType) throws SQLException {
        var raw = resultSet.getString(column);
        if (raw == null) {
            return null;
        }
        return Enum.valueOf(enumType, raw);
    }

    public @Nullable Component getComponent(String column) throws SQLException {
        var raw = resultSet.getString(column);
        if (raw == null) {
            return null;
        }
        return serializers.deserialize(raw, Component.class);
    }

    public <T> @Nullable T get(String column, Class<T> type) throws SQLException {
        var raw = resultSet.getObject(column);
        if (raw == null) {
            return null;
        }
        return serializers.deserialize(raw, type);
    }
}
