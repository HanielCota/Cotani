package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

public final class SoundSerializer implements ConfigSerializer<Sound> {

    @Override
    public Class<Sound> type() {
        return Sound.class;
    }

    @Override
    public Sound read(ConfigValue value) {
        var key = NamespacedKey.fromString(value.asString());
        if (key == null) {
            throw new ConfigException("Invalid sound key at " + value.location());
        }
        var sound = Registry.SOUNDS.get(key);
        if (sound != null) {
            return sound;
        }
        sound = Registry.SOUND_EVENT.get(key);
        if (sound != null) {
            return sound;
        }
        throw new ConfigException("Invalid sound '" + value.asString() + "' at " + value.location()
                + ". Custom resource-pack sounds may not be registered in the vanilla registry.");
    }

    @Override
    @SuppressWarnings("removal")
    public Object write(Sound value) {
        return value.key().toString();
    }
}
