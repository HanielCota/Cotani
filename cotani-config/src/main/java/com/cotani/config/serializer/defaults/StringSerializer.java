package com.cotani.config.serializer.defaults;

import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;

public final class StringSerializer implements ConfigSerializer<String> {

    @Override
    public Class<String> type() {
        return String.class;
    }

    @Override
    public String read(ConfigValue value) {
        if (value.raw() == null) {
            return "";
        }
        return String.valueOf(value.raw());
    }

    @Override
    public Object write(String value) {
        return value;
    }
}
