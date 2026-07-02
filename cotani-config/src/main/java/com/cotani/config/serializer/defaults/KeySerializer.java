package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import net.kyori.adventure.key.Key;

public final class KeySerializer implements ConfigSerializer<Key> {

    @Override
    public Class<Key> type() {
        return Key.class;
    }

    @Override
    public Key read(ConfigValue value) {
        try {
            return Key.key(value.asString());
        } catch (RuntimeException exception) {
            throw new ConfigException("Invalid key at " + value.location(), exception);
        }
    }

    @Override
    public Object write(Key value) {
        return value.asString();
    }
}
