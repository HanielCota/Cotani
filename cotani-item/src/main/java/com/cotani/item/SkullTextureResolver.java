package com.cotani.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SkullTextureResolver implements AutoCloseable {

    private static final String TEXTURES_PROPERTY = "textures";
    private static final String TEXTURES_DOMAIN = "https://textures.minecraft.net/texture/";
    private static final String HTTP_TEXTURES_DOMAIN = "http://textures.minecraft.net/texture/";
    private static final String BARE_DOMAIN = "textures.minecraft.net/texture/";

    private static final long DEFAULT_CACHE_EXPIRE_MINUTES = 60;
    private static final long DEFAULT_CACHE_MAXIMUM_SIZE = 1_000;

    private final Cache<String, PlayerProfile> profileCache;

    public SkullTextureResolver() {
        this(buildDefaultCache());
    }

    public SkullTextureResolver(Cache<String, PlayerProfile> profileCache) {
        this.profileCache = Objects.requireNonNull(profileCache, "profileCache");
    }

    public PlayerProfile fromBase64(String base64) {
        Objects.requireNonNull(base64, "Parameter 'base64' must not be null");
        return buildProfile(base64);
    }

    public PlayerProfile fromUrl(String textureUrl) {
        Objects.requireNonNull(textureUrl, "Parameter 'textureUrl' must not be null");
        var normalizedUrl = normalizeTextureUrl(textureUrl);
        return profileCache.get(normalizedUrl, SkullTextureResolver::createProfile);
    }

    public PlayerProfile fromUrl(URI textureUri) {
        Objects.requireNonNull(textureUri, "Parameter 'textureUri' must not be null");
        return fromUrl(textureUri.toString());
    }

    public void clearCache() {
        profileCache.invalidateAll();
    }

    @Override
    public void close() {
        clearCache();
    }

    private static Cache<String, PlayerProfile> buildDefaultCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(DEFAULT_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .maximumSize(DEFAULT_CACHE_MAXIMUM_SIZE)
                .build();
    }

    private static PlayerProfile createProfile(String normalizedUrl) {
        return buildProfile(toBase64Payload(normalizedUrl));
    }

    private static PlayerProfile buildProfile(String base64) {
        var uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
        var profile = Bukkit.createProfile(uuid);
        profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, base64));
        return profile;
    }

    private static String toBase64Payload(String normalizedUrl) {
        var payload = "{\"textures\":{\"SKIN\":{\"url\":\"" + escapeJson(normalizedUrl) + "\"}}}";
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeTextureUrl(String input) {
        var stripped = input.strip();
        var lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.startsWith(HTTP_TEXTURES_DOMAIN)) {
            return "https://" + stripped.substring(HTTP_TEXTURES_DOMAIN.length());
        }
        if (lower.startsWith(TEXTURES_DOMAIN)) {
            return stripped;
        }
        if (lower.startsWith(BARE_DOMAIN)) {
            return "https://" + stripped;
        }
        return TEXTURES_DOMAIN + stripped;
    }

    private static String escapeJson(String value) {
        var sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                        break;
                    }
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
