package com.cotani.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class AudienceMessagesTest {

    @Test
    void sendsMessage() {
        var audience = new CapturingAudience();

        AudienceMessages.sendMessage(audience, "<green>Hello");

        assertNotNull(audience.lastMessage);
        assertEquals(Component.text("Hello", NamedTextColor.GREEN), audience.lastMessage);
    }

    @Test
    void sendsMessageWithPlaceholder() {
        var audience = new CapturingAudience();

        AudienceMessages.sendMessage(audience, "<gray>Hello <name>", Placeholders.unparsed("name", "World"));

        assertEquals("Hello World", ComponentTexts.toPlain(audience.lastMessage));
    }

    @Test
    void sendsActionBar() {
        var audience = new CapturingAudience();

        AudienceMessages.sendActionBar(audience, "<red>Alert");

        assertNotNull(audience.lastActionBar);
        assertEquals(Component.text("Alert", NamedTextColor.RED), audience.lastActionBar);
    }

    @Test
    void sendsPlayerListHeader() {
        var audience = new CapturingAudience();

        AudienceMessages.sendPlayerListHeader(audience, "<blue>Header");

        assertNotNull(audience.lastPlayerListHeader);
        assertEquals(Component.text("Header", NamedTextColor.BLUE), audience.lastPlayerListHeader);
    }

    @Test
    void sendsPlayerListFooter() {
        var audience = new CapturingAudience();

        AudienceMessages.sendPlayerListFooter(audience, "<blue>Footer");

        assertNotNull(audience.lastPlayerListFooter);
        assertEquals(Component.text("Footer", NamedTextColor.BLUE), audience.lastPlayerListFooter);
    }

    @Test
    void sendsPlayerListHeaderAndFooter() {
        var audience = new CapturingAudience();

        AudienceMessages.sendPlayerListHeaderAndFooter(audience, "<blue>Header", "<green>Footer");

        assertNotNull(audience.lastPlayerListHeader);
        assertNotNull(audience.lastPlayerListFooter);
        assertEquals(Component.text("Header", NamedTextColor.BLUE), audience.lastPlayerListHeader);
        assertEquals(Component.text("Footer", NamedTextColor.GREEN), audience.lastPlayerListFooter);
    }

    private static final class CapturingAudience implements Audience {

        Component lastMessage = Component.empty();
        Component lastActionBar = Component.empty();
        Component lastPlayerListHeader = Component.empty();
        Component lastPlayerListFooter = Component.empty();

        @Override
        public void sendMessage(Component message) {
            lastMessage = message;
        }

        @Override
        public void sendActionBar(Component message) {
            lastActionBar = message;
        }

        @Override
        public void sendPlayerListHeader(Component header) {
            lastPlayerListHeader = header;
        }

        @Override
        public void sendPlayerListFooter(Component footer) {
            lastPlayerListFooter = footer;
        }

        @Override
        public void sendPlayerListHeaderAndFooter(Component header, Component footer) {
            lastPlayerListHeader = header;
            lastPlayerListFooter = footer;
        }
    }
}
