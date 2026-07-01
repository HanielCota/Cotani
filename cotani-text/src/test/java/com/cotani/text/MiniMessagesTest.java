package com.cotani.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.Test;

class MiniMessagesTest {

    @Test
    void parsesSimpleMiniMessage() {
        var component = MiniMessages.parse("<green>Hello");

        assertNotNull(component);
        assertEquals(Component.text("Hello", NamedTextColor.GREEN), component);
    }

    @Test
    void parsesWithUnparsedPlaceholder() {
        var component = MiniMessages.parse("<gray>Hello <name>", Placeholders.unparsed("name", "<red>World"));

        assertEquals("Hello <red>World", ComponentTexts.toPlain(component));
    }

    @Test
    void parsesWithComponentPlaceholder() {
        var name = Component.text("World", NamedTextColor.RED);
        var component = MiniMessages.parse("<gray>Hello <name>", Placeholders.component("name", name));

        assertEquals(Component.text("Hello ", NamedTextColor.GRAY).append(name), component);
    }

    @Test
    void serializesComponentToMiniMessage() {
        var component = Component.text("Hello", NamedTextColor.GREEN);

        assertEquals("<green>Hello", MiniMessages.serialize(component));
    }

    @Test
    void escapesTags() {
        assertEquals("\\<green>Hello", MiniMessages.escape("<green>Hello"));
    }

    @Test
    void stripsTags() {
        assertEquals("Hello", MiniMessages.strip("<green>Hello"));
    }

    @Test
    void parsesWithEmptyResolvers() {
        var component = MiniMessages.parse("<green>Hello", new TagResolver[0]);

        assertEquals(Component.text("Hello", NamedTextColor.GREEN), component);
    }
}
