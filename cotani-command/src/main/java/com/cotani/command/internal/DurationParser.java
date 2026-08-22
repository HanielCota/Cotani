package com.cotani.command.internal;

import com.cotani.api.InternalApi;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Internal parser for human-readable duration strings (e.g., "10s", "5m", "2h", "1d").
 */
@InternalApi
public final class DurationParser {
    private static final Pattern PATTERN = Pattern.compile(
            "(\\d+)(millis|ms|seconds|second|secs|sec|s|minutes|minute|mins|min|m|hours|hour|h|weeks|week|w|days|day|d)?",
            Pattern.CASE_INSENSITIVE);

    private DurationParser() {}

    public static Duration parse(String input) {
        Objects.requireNonNull(input, "input");
        if (input.length() > 64) {
            throw new IllegalArgumentException("Duration string is too long (max 64 characters)");
        }
        var trimmed = input.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Duration string must not be empty");
        }

        var matcher = PATTERN.matcher(trimmed);
        var foundAny = false;
        var totalMillis = 0L;
        var lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() != lastEnd) {
                throw new IllegalArgumentException("Invalid characters in duration format: '" + input + "'");
            }
            lastEnd = matcher.end();
            foundAny = true;

            var amount = Long.parseLong(matcher.group(1));
            var unit = matcher.group(2);
            if (unit == null || unit.isEmpty()) {
                unit = "s"; // default to seconds
            }

            var unitMillis =
                    switch (unit) {
                        case "ms", "millis" -> 1L;
                        case "s", "sec", "secs", "second", "seconds" -> 1_000L;
                        case "m", "min", "mins", "minute", "minutes" -> 60_000L;
                        case "h", "hour", "hours" -> 3_600_000L;
                        case "d", "day", "days" -> 86_400_000L;
                        case "w", "week", "weeks" -> 604_800_000L;
                        default ->
                            throw new IllegalArgumentException(
                                    "Unknown time unit '" + unit + "' in duration: " + input);
                    };

            try {
                totalMillis = Math.addExact(totalMillis, Math.multiplyExact(amount, unitMillis));
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException(
                        "Duration value overflows maximum duration limit: '" + input + "'", overflow);
            }
        }

        if (!foundAny || lastEnd != trimmed.length()) {
            throw new IllegalArgumentException("Invalid duration format: '" + input + "'");
        }

        return Duration.ofMillis(totalMillis);
    }
}
