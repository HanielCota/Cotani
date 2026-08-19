package com.cotani.cooldown.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CooldownActionTest {
    @Test
    void shouldCreateActionFromString() {
        var action = CooldownAction.of("daily.reward");

        assertEquals("daily.reward", action.value());
        assertEquals(CooldownAction.of("daily.reward"), action);
        assertNotEquals(CooldownAction.of("other"), action);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullValue() {
        assertThrows(NullPointerException.class, () -> CooldownAction.of(null));
    }

    @Test
    void shouldRejectBlankValue() {
        assertThrows(IllegalArgumentException.class, () -> CooldownAction.of(""));
        assertThrows(IllegalArgumentException.class, () -> CooldownAction.of("   "));
        assertThrows(IllegalArgumentException.class, () -> CooldownAction.of("\t\n"));
    }

    @Test
    void shouldAcceptMaximumLength() {
        var value = "a".repeat(64);

        assertEquals(value, CooldownAction.of(value).value());
    }

    @Test
    void shouldRejectValueExceedingMaximumLength() {
        var value = "a".repeat(65);

        assertThrows(IllegalArgumentException.class, () -> CooldownAction.of(value));
    }
}
