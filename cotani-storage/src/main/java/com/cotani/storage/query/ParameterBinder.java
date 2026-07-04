package com.cotani.storage.query;

import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class ParameterBinder {

    private final PreparedStatement statement;
    private final ValueSerializerRegistry serializers;
    private int index = 1;

    public ParameterBinder(PreparedStatement statement, ValueSerializerRegistry serializers) {
        this.statement = Objects.requireNonNull(statement, "statement");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    public ParameterBinder set(@Nullable Object value) throws SQLException {
        var serialized = fastSerialize(value);
        statement.setObject(index++, serialized);
        return this;
    }

    private @Nullable Object fastSerialize(@Nullable Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return serializers.serialize(value);
    }

    public ParameterBinder string(String value) throws SQLException {
        Objects.requireNonNull(value, "value");
        statement.setString(index, value);
        index++;
        return this;
    }

    public ParameterBinder uuid(UUID value) throws SQLException {
        Objects.requireNonNull(value, "value");
        statement.setString(index, value.toString());
        index++;
        return this;
    }

    public ParameterBinder integer(int value) throws SQLException {
        statement.setInt(index, value);
        index++;
        return this;
    }

    public ParameterBinder longValue(long value) throws SQLException {
        statement.setLong(index, value);
        index++;
        return this;
    }

    public ParameterBinder instant(Instant value) throws SQLException {
        Objects.requireNonNull(value, "value");
        statement.setString(index, value.toString());
        index++;
        return this;
    }

    public ParameterBinder duration(Duration value) throws SQLException {
        Objects.requireNonNull(value, "value");
        statement.setLong(index, value.toMillis());
        index++;
        return this;
    }
}
