package com.cotani.cooldown.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownTargetsTest {
    @Test
    void shouldCreateUserTarget() {
        var userId = UUID.randomUUID();

        assertEquals(new UserCooldownTarget(userId), CooldownTargets.user(userId));
    }

    @Test
    void shouldCreateGlobalTarget() {
        assertEquals(new GlobalCooldownTarget(), CooldownTargets.global());
    }

    @Test
    void shouldCreateResourceTarget() {
        assertEquals(new ResourceCooldownTarget("world:spawn"), CooldownTargets.resource("world:spawn"));
    }

    @Test
    void shouldTreatAllGlobalTargetsAsEqual() {
        assertEquals(new GlobalCooldownTarget(), new GlobalCooldownTarget());
        assertEquals(new GlobalCooldownTarget().hashCode(), new GlobalCooldownTarget().hashCode());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullUserId() {
        assertThrows(NullPointerException.class, () -> CooldownTargets.user(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullResourceId() {
        assertThrows(NullPointerException.class, () -> CooldownTargets.resource(null));
    }

    @Test
    void shouldRejectBlankResourceId() {
        assertThrows(IllegalArgumentException.class, () -> CooldownTargets.resource(""));
        assertThrows(IllegalArgumentException.class, () -> CooldownTargets.resource("   "));
    }

    @Test
    void shouldRejectOverlongResourceId() {
        var value = "a".repeat(128);
        var overlong = "a".repeat(129);

        assertEquals(value, ((ResourceCooldownTarget) CooldownTargets.resource(value)).resourceId());
        assertThrows(IllegalArgumentException.class, () -> CooldownTargets.resource(overlong));
    }

    @Test
    void shouldDistinguishTargetKinds() {
        var userId = UUID.randomUUID();

        assertTrue(CooldownTargets.user(userId) instanceof UserCooldownTarget);
        assertTrue(CooldownTargets.global() instanceof GlobalCooldownTarget);
        assertTrue(CooldownTargets.resource("spawn") instanceof ResourceCooldownTarget);
        assertNotEquals(CooldownTargets.user(userId), CooldownTargets.global());
        assertNotEquals(CooldownTargets.resource("spawn"), CooldownTargets.global());
    }
}
