package com.cotani.command.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationParserTest {

    @Test
    void shouldParseSingleUnits() {
        assertEquals(Duration.ofMillis(500), DurationParser.parse("500ms"));
        assertEquals(Duration.ofSeconds(10), DurationParser.parse("10s"));
        assertEquals(Duration.ofSeconds(10), DurationParser.parse("10sec"));
        assertEquals(Duration.ofSeconds(10), DurationParser.parse("10seconds"));
        assertEquals(Duration.ofMinutes(5), DurationParser.parse("5m"));
        assertEquals(Duration.ofMinutes(5), DurationParser.parse("5min"));
        assertEquals(Duration.ofMinutes(5), DurationParser.parse("5minutes"));
        assertEquals(Duration.ofHours(2), DurationParser.parse("2h"));
        assertEquals(Duration.ofHours(2), DurationParser.parse("2hours"));
        assertEquals(Duration.ofDays(1), DurationParser.parse("1d"));
        assertEquals(Duration.ofDays(1), DurationParser.parse("1day"));
        assertEquals(Duration.ofDays(7), DurationParser.parse("1w"));
        assertEquals(Duration.ofDays(7), DurationParser.parse("1week"));
    }

    @Test
    void shouldParseDefaultUnitAsSeconds() {
        assertEquals(Duration.ofSeconds(45), DurationParser.parse("45"));
    }

    @Test
    void shouldParseCombinedUnits() {
        var duration = DurationParser.parse("1h 30m 15s");
        assertEquals(Duration.ofHours(1).plusMinutes(30).plusSeconds(15), duration);

        var durationCompact = DurationParser.parse("2d12h");
        assertEquals(Duration.ofDays(2).plusHours(12), durationCompact);
    }

    @Test
    void shouldParseUpperAndMixedCase() {
        assertEquals(Duration.ofSeconds(10), DurationParser.parse("10S"));
        assertEquals(Duration.ofMinutes(5), DurationParser.parse("5M"));
        assertEquals(Duration.ofHours(2), DurationParser.parse("2H"));
        assertEquals(Duration.ofDays(1), DurationParser.parse("1D"));
        assertEquals(Duration.ofDays(7), DurationParser.parse("1W"));
    }

    @Test
    void shouldThrowOnInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("invalid"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("10x"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("10s extra"));
    }

    @Test
    void shouldThrowOnExcessiveLength() {
        var longInput = "1s ".repeat(25); // 75 chars > 64
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(longInput));
    }
}
