package com.cotani.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class ComponentTextsTest {

    @Test
    void createsEmptyComponent() {
        assertEquals(Component.empty(), ComponentTexts.empty());
    }

    @Test
    void createsTextComponent() {
        assertEquals(Component.text("Hello"), ComponentTexts.text("Hello"));
    }

    @Test
    void createsColoredTextComponent() {
        assertEquals(Component.text("Hello", NamedTextColor.RED), ComponentTexts.text("Hello", NamedTextColor.RED));
    }

    @Test
    void createsNewlineAndSpace() {
        assertEquals(Component.newline(), ComponentTexts.newline());
        assertEquals(Component.space(), ComponentTexts.space());
    }

    @Test
    void joinsComponents() {
        var joined = ComponentTexts.join(Component.text(", "), Component.text("a"), Component.text("b"));

        assertEquals("a, b", ComponentTexts.toPlain(joined));
    }

    @Test
    void joinsIterable() {
        var joined = ComponentTexts.join(Component.text(", "), List.of(Component.text("a"), Component.text("b")));

        assertEquals("a, b", ComponentTexts.toPlain(joined));
    }

    @Test
    void convertsFromPlain() {
        var component = ComponentTexts.fromPlain("Hello");

        assertEquals(Component.text("Hello"), component);
    }

    @Test
    void convertsFromLegacyAmpersand() {
        var component = ComponentTexts.fromLegacy("&aHello");

        assertEquals(Component.text("Hello", NamedTextColor.GREEN), component);
    }

    @Test
    void convertsFromLegacySection() {
        var component = ComponentTexts.fromLegacySection("§aHello");

        assertEquals(Component.text("Hello", NamedTextColor.GREEN), component);
    }

    @Test
    void convertsFromMiniMessage() {
        var component = ComponentTexts.fromMiniMessage("<green>Hello");

        assertEquals(Component.text("Hello", NamedTextColor.GREEN), component);
    }

    @Test
    void convertsToPlain() {
        var component = Component.text("Hello", NamedTextColor.GREEN);

        assertEquals("Hello", ComponentTexts.toPlain(component));
    }

    @Test
    void convertsToLegacyAmpersand() {
        var component = Component.text("Hello", NamedTextColor.GREEN);

        assertEquals("&aHello", ComponentTexts.toLegacy(component));
    }

    @Test
    void convertsToLegacySection() {
        var component = Component.text("Hello", NamedTextColor.GREEN);

        assertEquals("§aHello", ComponentTexts.toLegacySection(component));
    }

    @Test
    void convertsToMiniMessage() {
        var component = Component.text("Hello", NamedTextColor.GREEN);

        assertEquals("<green>Hello", ComponentTexts.toMiniMessage(component));
    }

    @Test
    void convertsToJson() {
        var component = Component.text("Hello");
        var json = ComponentTexts.toJson(component);

        assertFalse(json.isEmpty());
    }

    @Test
    void convertsFromJson() {
        var component = Component.text("Hello");
        var json = ComponentTexts.toJson(component);
        var parsed = ComponentTexts.fromJson(json);

        assertEquals(component, parsed);
    }

    @Test
    void removesItalics() {
        var component = Component.text("Hello").decorate(TextDecoration.ITALIC);
        var withoutItalics = ComponentTexts.withoutItalics(component);

        assertFalse(withoutItalics.hasDecoration(TextDecoration.ITALIC));
    }

    @Test
    void removesSpecificDecoration() {
        var component = Component.text("Hello").decorate(TextDecoration.BOLD);
        var withoutBold = ComponentTexts.withoutDecoration(component, TextDecoration.BOLD);

        assertFalse(withoutBold.hasDecoration(TextDecoration.BOLD));
    }

    @Test
    void compactsComponent() {
        var component = Component.text("Hello", NamedTextColor.GREEN).color(NamedTextColor.GREEN);
        var compact = ComponentTexts.compact(component);

        assertEquals("Hello", ComponentTexts.toPlain(compact));
    }
}
