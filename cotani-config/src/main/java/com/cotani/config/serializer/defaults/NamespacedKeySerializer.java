package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class NamespacedKeySerializer implements ConfigSerializer<NamespacedKey> {

    private final Plugin plugin;

    public NamespacedKeySerializer(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Class<NamespacedKey> type() {
        return NamespacedKey.class;
    }

    @Override
    public NamespacedKey read(ConfigValue value) {
        String input = value.asString();
        NamespacedKey key = NamespacedKey.fromString(input, plugin);
        if (key != null) {
            return key;
        }
        throw new ConfigException("Invalid namespaced key at " + value.location());
    }

    @Override
    public Object write(NamespacedKey value) {
        return value.toString();
    }
}
