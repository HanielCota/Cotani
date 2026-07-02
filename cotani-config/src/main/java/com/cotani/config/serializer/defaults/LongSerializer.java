package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;

public final class LongSerializer implements ConfigSerializer<Long> {

    @Override
    public Class<Long> type() {
        return Long.class;
    }

    @Override
    public Long read(ConfigValue value) {
        if (value.raw() instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.asString());
        } catch (NumberFormatException exception) {
            throw new ConfigException("Expected long at " + value.location(), exception);
        }
    }

    @Override
    public Object write(Long value) {
        return value;
    }
}
