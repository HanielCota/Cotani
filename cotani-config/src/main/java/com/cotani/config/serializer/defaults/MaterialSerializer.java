package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import java.util.Locale;
import org.bukkit.Material;

public final class MaterialSerializer implements ConfigSerializer<Material> {

    @Override
    public Class<Material> type() {
        return Material.class;
    }

    @Override
    public Material read(ConfigValue value) {
        Material material = Material.matchMaterial(value.asString().toUpperCase(Locale.ROOT));
        if (material != null) {
            return material;
        }
        throw new ConfigException("Invalid material at " + value.location());
    }

    @Override
    public Object write(Material value) {
        return value.name();
    }
}
