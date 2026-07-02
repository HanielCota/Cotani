package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;

public final class FloatSerializer implements ConfigSerializer<Float> {

    @Override
    public Class<Float> type() {
        return Float.class;
    }

    @Override
    public Float read(ConfigValue value) {
        if (value.raw() instanceof Number number) {
            return number.floatValue();
        }
        try {
            return Float.parseFloat(value.asString());
        } catch (NumberFormatException exception) {
            throw new ConfigException("Expected float at " + value.location(), exception);
        }
    }

    @Override
    public Object write(Float value) {
        return value;
    }
}
