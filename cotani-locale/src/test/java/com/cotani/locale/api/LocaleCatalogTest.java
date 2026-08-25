package com.cotani.locale.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocaleCatalogTest {
    @Test
    void resolvesRegionalThenLanguageThenDefaultFallback() {
        var english = LocaleId.of("en-US");
        var catalog = LocaleCatalog.builder(english)
                .bundle(MessageBundle.of(english, Map.of("welcome", "Hello")))
                .bundle(MessageBundle.of(LocaleId.of("pt"), Map.of("welcome", "Olá")))
                .build();

        var resolved = catalog.find(LocaleId.of("pt-BR"), MessageKey.of("welcome"));

        assertTrue(resolved.isPresent());
        assertEquals(LocaleId.of("pt"), resolved.orElseThrow().locale());
        assertEquals(
                List.of(LocaleId.of("pt-BR"), LocaleId.of("pt"), english, LocaleId.of("en")),
                catalog.fallbackChain(LocaleId.of("pt-BR")));
    }

    @Test
    void normalizesMessageKeysAndLocaleTags() {
        var bundle = MessageBundle.of(LocaleId.of("pt_br"), Map.of(" Welcome.Message ", "Olá"));

        assertEquals(LocaleId.of("pt-BR"), bundle.locale());
        assertEquals("Olá", bundle.find(MessageKey.of("WELCOME.MESSAGE")).orElseThrow());
    }

    @Test
    void rejectsMalformedLocaleTags() {
        assertThrows(IllegalArgumentException.class, () -> LocaleId.of("en--US"));
        assertThrows(IllegalArgumentException.class, () -> LocaleId.of("en-US-"));
        assertThrows(IllegalArgumentException.class, () -> LocaleId.of("<en>"));
    }

    @Test
    void preservesScriptDuringFallback() {
        var english = LocaleId.of("en-US");
        var catalog = LocaleCatalog.builder(english)
                .bundle(MessageBundle.of(english, Map.of("welcome", "Hello")))
                .bundle(MessageBundle.of(LocaleId.of("zh-Hant"), Map.of("welcome", "你好")))
                .build();

        var resolved = catalog.find(LocaleId.of("zh-Hant-TW"), MessageKey.of("welcome"));

        assertEquals(LocaleId.of("zh-Hant"), resolved.orElseThrow().locale());
        assertEquals(
                List.of(
                        LocaleId.of("zh-Hant-TW"),
                        LocaleId.of("zh-Hant"),
                        LocaleId.of("zh-TW"),
                        LocaleId.of("zh"),
                        english,
                        LocaleId.of("en")),
                catalog.fallbackChain(LocaleId.of("zh-Hant-TW")));
    }
}
