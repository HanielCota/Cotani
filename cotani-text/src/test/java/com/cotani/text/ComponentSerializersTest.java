package com.cotani.text;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ComponentSerializersTest {

    @Test
    void exposesGsonSerializer() {
        assertNotNull(ComponentSerializers.GSON);
        assertSame(GsonComponentSerializer.gson(), ComponentSerializers.GSON);
    }

    @Test
    void exposesPlainSerializer() {
        assertNotNull(ComponentSerializers.PLAIN);
        assertSame(PlainTextComponentSerializer.plainText(), ComponentSerializers.PLAIN);
    }

    @Test
    void exposesLegacySectionSerializer() {
        assertNotNull(ComponentSerializers.LEGACY_SECTION);
        assertSame(LegacyComponentSerializer.legacySection(), ComponentSerializers.LEGACY_SECTION);
    }

    @Test
    void exposesLegacyAmpersandSerializer() {
        assertNotNull(ComponentSerializers.LEGACY_AMPERSAND);
        assertSame(LegacyComponentSerializer.legacyAmpersand(), ComponentSerializers.LEGACY_AMPERSAND);
    }

    @Test
    void exposesMiniMessageSerializer() {
        assertNotNull(ComponentSerializers.MINIMESSAGE);
        assertSame(MiniMessage.miniMessage(), ComponentSerializers.MINIMESSAGE);
    }
}
