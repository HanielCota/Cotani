package com.cotani.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.jspecify.annotations.Nullable;
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

    @Test
    void sendsTitle() {
        var audience = new CapturingAudience();

        AudienceMessages.sendTitle(audience, "<red>Title", "<yellow>Subtitle");

        assertNotNull(audience.lastTitle);
        assertEquals(Component.text("Title", NamedTextColor.RED), audience.lastTitle.title());
        assertEquals(Component.text("Subtitle", NamedTextColor.YELLOW), audience.lastTitle.subtitle());
    }

    @Test
    void sendsTitleWithDuration() {
        var audience = new CapturingAudience();
        var fadeIn = java.time.Duration.ofSeconds(1);
        var stay = java.time.Duration.ofSeconds(2);
        var fadeOut = java.time.Duration.ofSeconds(3);

        AudienceMessages.sendTitle(audience, "<red>Title", "<yellow>Subtitle", fadeIn, stay, fadeOut);

        assertNotNull(audience.lastTitle);
        assertEquals(Component.text("Title", NamedTextColor.RED), audience.lastTitle.title());
        assertEquals(Component.text("Subtitle", NamedTextColor.YELLOW), audience.lastTitle.subtitle());
        assertNotNull(audience.lastTitle.times());
        assertEquals(fadeIn, audience.lastTitle.times().fadeIn());
        assertEquals(stay, audience.lastTitle.times().stay());
        assertEquals(fadeOut, audience.lastTitle.times().fadeOut());
    }

    private static final class CapturingAudience implements Audience {

        Component lastMessage = Component.empty();
        Component lastActionBar = Component.empty();
        Component lastPlayerListHeader = Component.empty();
        Component lastPlayerListFooter = Component.empty();

        @Nullable
        Title lastTitle;

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

        @Override
        public void showTitle(net.kyori.adventure.title.Title title) {
            lastTitle = title;
        }
    }
}
