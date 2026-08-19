package com.cotani.cooldown.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownKeyTest {
    @Test
    void shouldExposeTargetAndAction() {
        var target = new UserCooldownTarget(UUID.randomUUID());
        var action = CooldownAction.of("attack");

        var key = new CooldownKey(target, action);

        assertEquals(target, key.target());
        assertEquals(action, key.action());
    }

    @Test
    void shouldUseValueSemantics() {
        var key1 = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));
        var key2 = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));
        var other = new CooldownKey(CooldownTargets.global(), CooldownAction.of("other"));

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, other);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullTarget() {
        var action = CooldownAction.of("use");

        assertThrows(NullPointerException.class, () -> new CooldownKey(null, action));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullAction() {
        var target = CooldownTargets.global();

        assertThrows(NullPointerException.class, () -> new CooldownKey(target, null));
    }
}
