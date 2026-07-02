package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;

public final class DoubleSerializer implements ConfigSerializer<Double> {

    @Override
    public Class<Double> type() {
        return Double.class;
    }

    @Override
    public Double read(ConfigValue value) {
        if (value.raw() instanceof Number number) {
            var result = number.doubleValue();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                throw new ConfigException("Invalid numeric value at " + value.location());
            }
            return result;
        }
        try {
            var result = Double.parseDouble(value.asString());
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                throw new ConfigException("Invalid numeric value at " + value.location());
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new ConfigException("Expected double at " + value.location(), exception);
        }
    }

    @Override
    public Object write(Double value) {
        return value;
    }
}
