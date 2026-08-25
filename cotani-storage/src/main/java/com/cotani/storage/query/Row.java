package com.cotani.storage.query;

import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public final class Row {
    private final ResultSet resultSet;
    private final ValueSerializerRegistry serializers;

    public Row(ResultSet resultSet, ValueSerializerRegistry serializers) {
        this.resultSet = Objects.requireNonNull(resultSet, "resultSet");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    public String getString(String column) throws SQLException {
        return Objects.requireNonNull(resultSet.getString(column), () -> "Column is SQL NULL: " + column);
    }

    public Optional<String> getStringOptional(String column) throws SQLException {
        return Optional.ofNullable(resultSet.getString(column));
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

    public Optional<Integer> getIntOptional(String column) throws SQLException {
        int value = resultSet.getInt(column);

        if (resultSet.wasNull()) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    public Optional<Long> getLongOptional(String column) throws SQLException {
        long value = resultSet.getLong(column);

        if (resultSet.wasNull()) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    public Optional<Double> getDoubleOptional(String column) throws SQLException {
        double value = resultSet.getDouble(column);

        if (resultSet.wasNull()) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    public Optional<Boolean> getBooleanOptional(String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);

        if (resultSet.wasNull()) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    public Optional<UUID> getUuidOptional(String column) throws SQLException {
        var raw = resultSet.getString(column);

        if (raw == null) {
            return Optional.empty();
        }

        return Optional.of(UUID.fromString(raw));
    }

    public Optional<Instant> getInstantOptional(String column) throws SQLException {
        return Optional.ofNullable(JdbcInstantCodec.read(resultSet, column));
    }

    public Optional<Duration> getDurationOptional(String column) throws SQLException {
        long value = resultSet.getLong(column);

        if (resultSet.wasNull()) {
            return Optional.empty();
        }

        return Optional.of(Duration.ofMillis(value));
    }

    public <E extends Enum<E>> Optional<E> getEnumOptional(String column, Class<E> enumType) throws SQLException {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(enumType, "enumType");
        var raw = resultSet.getString(column);

        if (raw == null) {
            return Optional.empty();
        }

        return Optional.of(Enum.valueOf(enumType, raw));
    }

    public Optional<Component> getComponentOptional(String column) throws SQLException {
        var raw = resultSet.getString(column);

        if (raw == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(serializers.deserialize(raw, Component.class));
    }

    public <T> Optional<T> getOptional(String column, Class<T> type) throws SQLException {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(type, "type");
        var raw = resultSet.getObject(column);

        if (raw == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(serializers.deserialize(raw, type));
    }
}
