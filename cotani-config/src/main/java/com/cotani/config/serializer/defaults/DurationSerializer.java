package com.cotani.config.serializer.defaults;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationSerializer implements ConfigSerializer<Duration> {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h|d)$", Pattern.CASE_INSENSITIVE);

    @Override
    public Class<Duration> type() {
        return Duration.class;
    }

    @Override
    public Duration read(ConfigValue value) {
        if (value.raw() instanceof Duration duration) {
            return duration;
        }
        String input = value.asString().trim();
        if (input.startsWith("PT")) {
            return Duration.parse(input);
        }
        Matcher matcher = PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new ConfigException("Invalid duration at " + value.location() + ". Use 500ms, 3s, 5m, 2h or 1d.");
        }
        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new ConfigException("Duration amount out of range at " + value.location(), exception);
        }
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new ConfigException("Invalid duration unit " + unit + " at " + value.location());
        };
    }

    @Override
    public Object write(Duration value) {
        if (value.isZero()) {
            return "0ms";
        }
        if (value.toMillis() % 1000 != 0) return value.toMillis() + "ms";
        if (value.toSeconds() % 60 != 0) return value.toSeconds() + "s";
        if (value.toMinutes() % 60 != 0) return value.toMinutes() + "m";
        if (value.toHours() % 24 != 0) return value.toHours() + "h";
        return value.toDays() + "d";
    }
}
