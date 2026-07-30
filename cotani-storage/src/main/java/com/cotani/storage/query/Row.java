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
import org.jspecify.annotations.Nullable;

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

    /** @deprecated use {@link #getIntOptional(String)} */
    @Deprecated(forRemoval = false)
    public @Nullable Integer getIntOrNull(String column) throws SQLException {
        int value = resultSet.getInt(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    public Optional<Integer> getIntOptional(String column) throws SQLException {
        return Optional.ofNullable(getIntOrNull(column));
    }

    /** @deprecated use {@link #getLongOptional(String)} */
    @Deprecated(forRemoval = false)
    public @Nullable Long getLongOrNull(String column) throws SQLException {
        long value = resultSet.getLong(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    public Optional<Long> getLongOptional(String column) throws SQLException {
        return Optional.ofNullable(getLongOrNull(column));
    }

    /** @deprecated use {@link #getDoubleOptional(String)} */
    @Deprecated(forRemoval = false)
    public @Nullable Double getDoubleOrNull(String column) throws SQLException {
        double value = resultSet.getDouble(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    public Optional<Double> getDoubleOptional(String column) throws SQLException {
        return Optional.ofNullable(getDoubleOrNull(column));
    }

    /** @deprecated use {@link #getBooleanOptional(String)} */
    @Deprecated(forRemoval = false)
    public @Nullable Boolean getBooleanOrNull(String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    public Optional<Boolean> getBooleanOptional(String column) throws SQLException {
        return Optional.ofNullable(getBooleanOrNull(column));
    }

    /** @deprecated use {@link #getUuidOptional(String)} */
    @Deprecated(forRemoval = false)
    public @Nullable UUID getUuid(String column) throws SQLException {
        var raw = resultSet.getString(column);
        if (raw == null) {
            return null;
        }
        return UUID.fromString(raw);
    }

    public Optional<UUID> getUuidOptional(String column) throws SQLException {
        return Optional.ofNullable(getUuid(column));
    }

    /** @deprecated use {@link #getInstantOptional(String)} */
    @Deprecated(forRemoval = false)
    public @Nullable Instant getInstant(String column) throws SQLException {
        return JdbcInstantCodec.read(resultSet, column);
    }

    public Optional<Instant> getInstantOptional(String column) throws SQLException {
        return Optional.ofNullable(getInstant(column));
    }

    /** @deprecated use {@link #getDurationOptional(String)} */
    @Deprecated(forRemoval = false)
    public @Nullable Duration getDuration(String column) throws SQLException {
        long value = resultSet.getLong(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return Duration.ofMillis(value);
    }

    public Optional<Duration> getDurationOptional(String column) throws SQLException {
        return Optional.ofNullable(getDuration(column));
    }

    /** @deprecated use {@link #getEnumOptional(String, Class)} */
    @Deprecated(forRemoval = false)
    public <E extends Enum<E>> @Nullable E getEnum(String column, Class<E> enumType) throws SQLException {
        var raw = resultSet.getString(column);
        if (raw == null) {
            return null;
        }
        return Enum.valueOf(enumType, raw);
    }

    public <E extends Enum<E>> Optional<E> getEnumOptional(String column, Class<E> enumType) throws SQLException {
        return Optional.ofNullable(getEnum(column, enumType));
    }

    /** @deprecated use {@link #getComponentOptional(String)} */
    @Deprecated(forRemoval = false)
    public @Nullable Component getComponent(String column) throws SQLException {
        var raw = resultSet.getString(column);
        if (raw == null) {
            return null;
        }
        return serializers.deserialize(raw, Component.class);
    }

    public Optional<Component> getComponentOptional(String column) throws SQLException {
        return Optional.ofNullable(getComponent(column));
    }

    /** @deprecated use {@link #getOptional(String, Class)} */
    @Deprecated(forRemoval = false)
    public <T> @Nullable T get(String column, Class<T> type) throws SQLException {
        var raw = resultSet.getObject(column);
        if (raw == null) {
            return null;
        }
        return serializers.deserialize(raw, type);
    }

    public <T> Optional<T> getOptional(String column, Class<T> type) throws SQLException {
        return Optional.ofNullable(get(column, type));
    }
}
