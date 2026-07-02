package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import java.util.UUID;

public final class UuidSerializer implements ConfigSerializer<UUID> {

    @Override
    public Class<UUID> type() {
        return UUID.class;
    }

    @Override
    public UUID read(ConfigValue value) {
        try {
            return UUID.fromString(value.asString());
        } catch (IllegalArgumentException exception) {
            throw new ConfigException("Invalid UUID at " + value.location(), exception);
        }
    }

    @Override
    public Object write(UUID value) {
        return value.toString();
    }
}
