package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import com.cotani.text.MiniMessages;
import net.kyori.adventure.text.Component;

public final class ComponentSerializer implements ConfigSerializer<Component> {

    @Override
    public Class<Component> type() {
        return Component.class;
    }

    @Override
    public Component read(ConfigValue value) {
        try {
            return MiniMessages.parse(value.asString());
        } catch (RuntimeException exception) {
            throw new ConfigException("Invalid component format at " + value.location(), exception);
        }
    }

    @Override
    public Object write(Component value) {
        return MiniMessages.serialize(value);
    }
}
