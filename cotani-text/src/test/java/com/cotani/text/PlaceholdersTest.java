package com.cotani.text;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    @Test
    void createsComponentPlaceholder() {
        var resolver = Placeholders.component("name", Component.text("World"));

        assertNotNull(resolver);
    }

    @Test
    void createsUnparsedPlaceholder() {
        var component = MiniMessages.parse("<msg>", Placeholders.unparsed("msg", "<red>x"));

        assertEquals("<red>x", ComponentTexts.toPlain(component));
    }

    @Test
    void createsParsedPlaceholder() {
        var component = MiniMessages.parse("<msg>", Placeholders.parsed("msg", "<red>x"));

        assertEquals("x", ComponentTexts.toPlain(component));
        assertEquals(NamedTextColor.RED, component.color());
    }

    @Test
    void createsStylingPlaceholder() {
        var component = MiniMessages.parse(
                "<fancy>Hello</fancy>", Placeholders.styling("fancy", NamedTextColor.GOLD, TextDecoration.BOLD));

        assertEquals(NamedTextColor.GOLD, component.color());
        assertEquals(TextDecoration.State.TRUE, component.decoration(TextDecoration.BOLD));
    }

    @Test
    void createsNumberPlaceholder() {
        var component = MiniMessages.parse("<no>", Placeholders.number("no", 250.25));

        assertTrue(ComponentTexts.toPlain(component).matches("250[.,]25"));
    }

    @Test
    void createsNumberPlaceholderWithPatternArgument() {
        var component = MiniMessages.parse("<no:'#.00'>", Placeholders.number("no", 250.25));

        assertTrue(ComponentTexts.toPlain(component).matches("250[.,]25"));
    }

    @Test
    void createsDatePlaceholder() {
        var date = LocalDate.of(2024, 5, 27);
        var component = MiniMessages.parse("<date:'yyyy-MM-dd'>", Placeholders.date("date", date));

        assertEquals("2024-05-27", ComponentTexts.toPlain(component));
    }

    @Test
    void createsChoicePlaceholder() {
        var component = MiniMessages.parse("<choice:'0#no|1#one|1<many'>", Placeholders.choice("choice", 5));

        assertEquals("many", ComponentTexts.toPlain(component));
    }

    @Test
    void createsBooleanChoicePlaceholder() {
        var component = MiniMessages.parse("<bool:'yes':'no'>", Placeholders.booleanChoice("bool", true));

        assertEquals("yes", ComponentTexts.toPlain(component));
    }

    @Test
    void createsJoiningPlaceholder() {
        var component = MiniMessages.parse(
                "<items:', '>", Placeholders.joining("items", Component.text("a"), Component.text("b")));

        assertEquals("a, b", ComponentTexts.toPlain(component));
    }

    @Test
    void createsJoiningPlaceholderFromIterable() {
        var items = List.of(Component.text("a"), Component.text("b"));
        var component = MiniMessages.parse("<items:', '>", Placeholders.joining("items", items));

        assertEquals("a, b", ComponentTexts.toPlain(component));
    }

    @Test
    void combinesResolvers() {
        TagResolver combined = Placeholders.combine(Placeholders.unparsed("a", "A"), Placeholders.unparsed("b", "B"));
        var component = MiniMessages.parse("<a><b>", combined);

        assertEquals("AB", ComponentTexts.toPlain(component));
    }
}
