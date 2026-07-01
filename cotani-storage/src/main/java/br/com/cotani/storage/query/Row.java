package br.com.cotani.storage.query;

import br.com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public final class Row {

    private final ResultSet resultSet;
    private final ValueSerializerRegistry serializers;

    public Row(ResultSet resultSet, ValueSerializerRegistry serializers) {
        this.resultSet = resultSet;
        this.serializers = serializers;
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

    public UUID getUuid(String column) throws SQLException {
        return UUID.fromString(resultSet.getString(column));
    }

    public Instant getInstant(String column) throws SQLException {
        return Instant.parse(resultSet.getString(column));
    }

    public Duration getDuration(String column) throws SQLException {
        return Duration.ofMillis(resultSet.getLong(column));
    }

    public <E extends Enum<E>> E getEnum(String column, Class<E> enumType) throws SQLException {
        return Enum.valueOf(enumType, resultSet.getString(column));
    }

    public Component getComponent(String column) throws SQLException {
        return serializers.deserialize(resultSet.getString(column), Component.class);
    }

    public <T> T get(String column, Class<T> type) throws SQLException {
        return serializers.deserialize(resultSet.getObject(column), type);
    }
}
