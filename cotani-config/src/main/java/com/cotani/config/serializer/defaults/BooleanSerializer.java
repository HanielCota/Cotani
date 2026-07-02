package com.cotani.config.serializer.defaults;

import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import java.util.Locale;
import java.util.Set;

public final class BooleanSerializer implements ConfigSerializer<Boolean> {

    private static final Set<String> TRUE_VALUES = Set.of("true", "yes", "on", "1");
    private static final Set<String> FALSE_VALUES = Set.of("false", "no", "off", "0");

    @Override
    public Class<Boolean> type() {
        return Boolean.class;
    }

    @Override
    public Boolean read(ConfigValue value) {
        if (value.raw() instanceof Boolean bool) {
            return bool;
        }
        var text = value.asString().trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(text)) {
            return true;
        }
        if (FALSE_VALUES.contains(text)) {
            return false;
        }
        throw new com.cotani.config.exception.ConfigException("Invalid boolean value '" + value.raw() + "' at "
                + value.location() + ". Accepted: true/false, yes/no, on/off, 1/0");
    }

    @Override
    public Object write(Boolean value) {
        return value;
    }
}
