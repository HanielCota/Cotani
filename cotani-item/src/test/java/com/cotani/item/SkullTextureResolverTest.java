package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URI;
import org.junit.jupiter.api.Test;

class SkullTextureResolverTest {

    private static String normalize(String input) throws Exception {
        return invokeStatic("normalizeTextureUrl", String.class, input);
    }

    private static String escape(String input) throws Exception {
        return invokeStatic("escapeJson", String.class, input);
    }

    private static String invokeStatic(String name, Class<?> paramType, String value)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        var method = SkullTextureResolver.class.getDeclaredMethod(name, paramType);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }

    @Test
    void isFinalAndConstructable() throws NoSuchMethodException {
        assertTrue(Modifier.isFinal(SkullTextureResolver.class.getModifiers()));

        var noArg = SkullTextureResolver.class.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(noArg.getModifiers()));

        assertTrue(Modifier.isPublic(SkullTextureResolver.class
                .getDeclaredConstructor(com.github.benmanes.caffeine.cache.Cache.class)
                .getModifiers()));

        assertTrue(AutoCloseable.class.isAssignableFrom(SkullTextureResolver.class));
        SkullTextureResolver.class.getMethod("close");
    }

    @Test
    void exposesExpectedApi() throws NoSuchMethodException {
        SkullTextureResolver.class.getMethod("fromBase64", String.class);
        SkullTextureResolver.class.getMethod("fromUrl", String.class);
        SkullTextureResolver.class.getMethod("fromUrl", URI.class);
    }

    @Test
    void normalizesFullUrl() throws Exception {
        assertEquals(
                "https://textures.minecraft.net/texture/abc123",
                normalize("https://textures.minecraft.net/texture/abc123"));
    }

    @Test
    void normalizesHttpUrl() throws Exception {
        assertEquals(
                "https://textures.minecraft.net/texture/abc123",
                normalize("http://textures.minecraft.net/texture/abc123"));
    }

    @Test
    void normalizesDomainOnlyUrl() throws Exception {
        assertEquals(
                "https://textures.minecraft.net/texture/abc123", normalize("textures.minecraft.net/texture/abc123"));
    }

    @Test
    void normalizesTextureIdOnly() throws Exception {
        assertEquals("https://textures.minecraft.net/texture/abc123", normalize("abc123"));
    }

    @Test
    void escapesJsonSpecialCharacters() throws Exception {
        assertEquals("https://\\\\example.com/\\\"test\\\"", escape("https://\\example.com/\"test\""));
    }
}
