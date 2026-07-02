package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;

public final class IntegerSerializer implements ConfigSerializer<Integer> {

    @Override
    public Class<Integer> type() {
        return Integer.class;
    }

    @Override
    public Integer read(ConfigValue value) {
        if (value.raw() instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.asString());
        } catch (NumberFormatException exception) {
            throw new ConfigException("Expected integer at " + value.location(), exception);
        }
    }

    @Override
    public Object write(Integer value) {
        return value;
    }
}
