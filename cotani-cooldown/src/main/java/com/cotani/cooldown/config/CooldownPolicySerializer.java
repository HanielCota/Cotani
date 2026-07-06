package com.cotani.cooldown.config;

import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import com.cotani.cooldown.CooldownPolicy;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CooldownPolicySerializer implements ConfigSerializer<CooldownPolicy> {

    private final ConfigSerializer<Duration> durationSerializer;

    public CooldownPolicySerializer(ConfigSerializer<Duration> durationSerializer) {
        this.durationSerializer = Objects.requireNonNull(durationSerializer, "durationSerializer");
    }

    @Override
    public Class<CooldownPolicy> type() {
        return CooldownPolicy.class;
    }

    @Override
    public CooldownPolicy read(ConfigValue value) {
        Objects.requireNonNull(value, "value");
        Duration duration = durationSerializer.read(value);
        return () -> duration;
    }

    @Override
    public Object write(CooldownPolicy value) {
        Objects.requireNonNull(value, "value");
        return durationSerializer.write(value.duration());
    }
}
