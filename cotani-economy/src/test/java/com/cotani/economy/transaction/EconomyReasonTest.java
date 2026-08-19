package com.cotani.economy.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyReasonTest {

    @Test
    void shouldCreateSystemReasonWithCotaniSource() {
        var reason = EconomyReason.system("reward");

        assertEquals("reward", reason.key());
        assertEquals("cotani", reason.source());
        assertTrue(reason.actor().isEmpty());
    }

    @Test
    void shouldCreatePluginReasonWithCustomSource() {
        var reason = EconomyReason.plugin("purchase", "shop-plugin");

        assertEquals("purchase", reason.key());
        assertEquals("shop-plugin", reason.source());
        assertTrue(reason.actor().isEmpty());
    }

    @Test
    void shouldCreatePlayerReasonWithActor() {
        var actor = UUID.randomUUID();

        var reason = EconomyReason.player("tip", actor);

        assertEquals("tip", reason.key());
        assertEquals("player", reason.source());
        assertTrue(reason.actor().isPresent());
        assertEquals(actor, reason.actor().orElseThrow());
    }

    @Test
    void shouldNormalizeKeyAndSourceToLowerCaseAndTrim() {
        var reason = EconomyReason.system("  Shop_Reward  ");

        assertEquals("shop_reward", reason.key());
        assertEquals("cotani", reason.source());
    }

    @Test
    void shouldAcceptValidKeyCharactersIncludingDotsColonsAndDashes() {
        assertTrue(EconomyReason.system("a.b:c-d_1").key().matches("^[a-z0-9_.:-]{2,96}$"));
    }

    @Test
    void shouldRejectKeyThatDoesNotMatchAllowedPattern() {
        assertThrows(IllegalArgumentException.class, () -> EconomyReason.system("A B"));
        assertThrows(IllegalArgumentException.class, () -> EconomyReason.system("a"));
        assertThrows(IllegalArgumentException.class, () -> EconomyReason.system("a".repeat(97)));
        assertThrows(IllegalArgumentException.class, () -> EconomyReason.system("with space"));
    }

    @Test
    void shouldAcceptUppercaseKeysByNormalizingThemFirst() {
        var reason = EconomyReason.system("UPPER");

        assertEquals("upper", reason.key());
    }

    @Test
    void shouldRejectSourceThatDoesNotMatchAllowedPattern() {
        assertThrows(IllegalArgumentException.class, () -> EconomyReason.plugin("tip", "S"));
        assertThrows(IllegalArgumentException.class, () -> EconomyReason.plugin("tip", "s".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> EconomyReason.plugin("tip", "bad source"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullKeyAndSource() {
        assertThrows(NullPointerException.class, () -> EconomyReason.system(null));
        assertThrows(NullPointerException.class, () -> EconomyReason.plugin("tip", null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullActorForPlayerReason() {
        assertThrows(NullPointerException.class, () -> EconomyReason.player("tip", null));
    }

    @Test
    void shouldImplementValueEquality() {
        var actor = UUID.randomUUID();

        assertEquals(EconomyReason.system("tip"), EconomyReason.system("tip"));
        assertEquals(EconomyReason.player("tip", actor), EconomyReason.player("tip", actor));
        assertNotEquals(EconomyReason.system("tip"), EconomyReason.system("other"));
        assertNotEquals(EconomyReason.system("tip"), EconomyReason.player("tip", actor));
    }
}
