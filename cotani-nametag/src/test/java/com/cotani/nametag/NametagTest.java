package com.cotani.nametag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.nametag.api.CollisionRule;
import com.cotani.nametag.api.Nametag;
import com.cotani.nametag.api.NametagVisibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class NametagTest {

    @Test
    void shouldHaveSensibleDefaults() {
        var nametag = Nametag.EMPTY;

        assertEquals(Nametag.DEFAULT_PRIORITY, nametag.priority());
        assertEquals(Component.empty(), nametag.prefix());
        assertEquals(Component.empty(), nametag.suffix());
        assertNull(nametag.color());
        assertEquals(NametagVisibility.ALWAYS, nametag.visibility());
        assertEquals(CollisionRule.ALWAYS, nametag.collisionRule());
        assertFalse(nametag.seeFriendlyInvisibles());
        assertTrue(nametag.friendlyFire());
    }

    @Test
    void shouldBuildCustomNametagWithMiniMessage() {
        var nametag = Nametag.builder()
                .priority(10)
                .prefix("<red>[Admin]</red> ")
                .suffix(" <gray>[VIP]</gray>")
                .color(NamedTextColor.RED)
                .visibility(NametagVisibility.HIDE_FOR_OTHER_TEAMS)
                .collisionRule(CollisionRule.NEVER)
                .seeFriendlyInvisibles(true)
                .friendlyFire(false)
                .build();

        assertEquals(10, nametag.priority());
        assertEquals(NamedTextColor.RED, nametag.color());
        assertEquals(NametagVisibility.HIDE_FOR_OTHER_TEAMS, nametag.visibility());
        assertEquals(CollisionRule.NEVER, nametag.collisionRule());
        assertTrue(nametag.seeFriendlyInvisibles());
        assertFalse(nametag.friendlyFire());
    }

    @Test
    void shouldBuildEscapedPrefixAndSuffix() {
        var nametag = Nametag.builder()
                .prefixEscaped("<not_a_tag>Player")
                .suffixEscaped("<another_tag>")
                .build();

        // The parsed component should contain the literal text '<not_a_tag>Player' without parsing as tag
        assertEquals(Component.text("<not_a_tag>Player"), nametag.prefix());
        assertEquals(Component.text("<another_tag>"), nametag.suffix());
    }

    @Test
    void shouldRebuildViaToBuilder() {
        var original = Nametag.of(Component.text("[Mod] "), Component.text(" [Lvl 5]"), 50);
        var modified =
                original.toBuilder().priority(20).color(NamedTextColor.GOLD).build();

        assertEquals(20, modified.priority());
        assertEquals(original.prefix(), modified.prefix());
        assertEquals(original.suffix(), modified.suffix());
        assertEquals(NamedTextColor.GOLD, modified.color());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldValidateArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Nametag.builder().priority(-1).build());
        assertThrows(
                NullPointerException.class,
                () -> Nametag.builder().prefix((Component) null).build());
        assertThrows(
                NullPointerException.class,
                () -> Nametag.builder().suffix((Component) null).build());
        assertThrows(
                NullPointerException.class,
                () -> Nametag.builder().visibility(null).build());
        assertThrows(
                NullPointerException.class,
                () -> Nametag.builder().collisionRule(null).build());
    }
}
