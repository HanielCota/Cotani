package com.cotani.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SkullTextureResolver {

    private static final String TEXTURES_PROPERTY = "textures";
    private static final String TEXTURES_DOMAIN = "https://textures.minecraft.net/texture/";

    private static final long DEFAULT_CACHE_EXPIRE_MINUTES = 10;
    private static final long DEFAULT_CACHE_MAXIMUM_SIZE = 1_000;

    private static final Cache<String, String> PAYLOAD_CACHE = Caffeine.newBuilder()
            .expireAfterAccess(DEFAULT_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .maximumSize(DEFAULT_CACHE_MAXIMUM_SIZE)
            .build();

    private SkullTextureResolver() {}

    public static PlayerProfile fromBase64(String base64) {
        Objects.requireNonNull(base64, "Parameter 'base64' must not be null");
        var profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, base64));
        return profile;
    }

    public static PlayerProfile fromUrl(String textureUrl) {
        Objects.requireNonNull(textureUrl, "Parameter 'textureUrl' must not be null");
        var normalizedUrl = normalizeTextureUrl(textureUrl);
        var payload = PAYLOAD_CACHE.get(normalizedUrl, SkullTextureResolver::toBase64Payload);
        return fromBase64(payload);
    }

    public static PlayerProfile fromUrl(URI textureUri) {
        Objects.requireNonNull(textureUri, "Parameter 'textureUri' must not be null");
        return fromUrl(textureUri.toString());
    }

    private static String toBase64Payload(String normalizedUrl) {
        var payload = "{\"textures\":{\"SKIN\":{\"url\":\"" + escapeJson(normalizedUrl) + "\"}}}";
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeTextureUrl(String input) {
        var trimmed = input.trim();
        if (trimmed.startsWith(TEXTURES_DOMAIN)) {
            return trimmed;
        }
        if (trimmed.startsWith("textures.minecraft.net/texture/")) {
            return "https://" + trimmed;
        }
        return TEXTURES_DOMAIN + trimmed;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
