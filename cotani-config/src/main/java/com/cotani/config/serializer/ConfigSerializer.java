package com.cotani.config.serializer;

import com.cotani.config.value.ConfigValue;

public interface ConfigSerializer<T> {

    Class<T> type();

    T read(ConfigValue value);

    Object write(T value);
}
