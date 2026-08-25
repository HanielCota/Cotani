package com.cotani.locale.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.text.ComponentTexts;
import com.cotani.text.MiniMessages;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class MessageArgumentsTest {
    @Test
    void rendersSafeTextAndComponentArguments() {
        var arguments = MessageArguments.builder()
                .text("name", "<red>Steve")
                .component("rank", Component.text("VIP"))
                .build();

        var rendered = MiniMessages.parse("<rank> <name>", arguments.resolvers());

        assertEquals("VIP <red>Steve", ComponentTexts.toPlain(rendered));
    }

    @Test
    void rendersPluralChoiceArgument() {
        var arguments = MessageArguments.builder().plural("items", 3).build();

        var rendered = MiniMessages.parse("<items:'0#none|1#one item|1<many items'>", arguments.resolvers());

        assertEquals("many items", ComponentTexts.toPlain(rendered));
    }

    @Test
    void messageBundleCopiesInputMap() {
        var messages = new java.util.HashMap<String, String>();
        messages.put("welcome", "Hello");
        var bundle = MessageBundle.of(LocaleId.of("en"), messages);

        messages.put("later", "Not visible");

        assertEquals(Map.of(MessageKey.of("welcome"), "Hello"), bundle.messages());
    }

    @Test
    void rejectsDuplicateNamedArguments() {
        var builder = MessageArguments.builder().text("name", "Steve");

        assertThrows(IllegalArgumentException.class, () -> builder.component("NAME", Component.text("Alex")));
    }
}
