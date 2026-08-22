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
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SkullTextureResolver implements AutoCloseable {
    private static final String TEXTURES_PROPERTY = "textures";
    private static final String TEXTURES_DOMAIN = "https://textures.minecraft.net/texture/";
    private static final String HTTP_TEXTURES_DOMAIN = "http://textures.minecraft.net/texture/";
    private static final String BARE_DOMAIN = "textures.minecraft.net/texture/";

    private static final long DEFAULT_CACHE_EXPIRE_MINUTES = 60;
    private static final long DEFAULT_CACHE_MAXIMUM_SIZE = 1_000;
    private static final int MAX_BASE64_LENGTH = 16_384;
    private static final int MAX_TEXTURE_URL_LENGTH = 2_048;

    private final @Nullable Cache<String, PlayerProfile> profileCache;

    private static final SkullTextureResolver UNCACHED = new SkullTextureResolver(null, UncachedMarker.INSTANCE);

    public SkullTextureResolver() {
        this(buildDefaultCache());
    }

    /** Returns a resolver without shared or retained cache state. */
    public static SkullTextureResolver uncached() {
        return UNCACHED;
    }

    public SkullTextureResolver(Cache<String, PlayerProfile> profileCache) {
        this.profileCache = Objects.requireNonNull(profileCache, "profileCache");
    }

    private SkullTextureResolver(@Nullable Cache<String, PlayerProfile> profileCache, UncachedMarker ignored) {
        this.profileCache = profileCache;
        Objects.requireNonNull(ignored, "ignored");
    }

    private enum UncachedMarker {
        INSTANCE
    }

    /**
     * Resolves a bounded texture payload into a Paper profile.
     *
     * <p>Because profile creation calls the Bukkit API, invoke this method only from a
     * server-owned thread that is valid for the calling plugin.
     */
    public PlayerProfile fromBase64(String base64) {
        Objects.requireNonNull(base64, "Parameter 'base64' must not be null");

        requireBoundedInput(base64, "base64", MAX_BASE64_LENGTH);
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Parameter 'base64' must be valid Base64", failure);
        }
        requireTrustedSkinUrl(new String(decoded, StandardCharsets.UTF_8));

        if (profileCache != null) {
            return profileCache.get(base64, SkullTextureResolver::buildProfile);
        }

        return buildProfile(base64);
    }

    /**
     * Resolves a bounded texture URL into a Paper profile.
     *
     * <p>Because profile creation calls the Bukkit API, invoke this method only from a
     * server-owned thread that is valid for the calling plugin.
     */
    public PlayerProfile fromUrl(String textureUrl) {
        Objects.requireNonNull(textureUrl, "Parameter 'textureUrl' must not be null");

        var normalizedUrl = normalizeTextureUrl(textureUrl);

        if (profileCache != null) {
            return profileCache.get(normalizedUrl, SkullTextureResolver::createProfile);
        }

        return createProfile(normalizedUrl);
    }

    public PlayerProfile fromUrl(URI textureUri) {
        Objects.requireNonNull(textureUri, "Parameter 'textureUri' must not be null");

        return fromUrl(textureUri.toString());
    }

    public void clearCache() {
        if (profileCache != null) {
            profileCache.invalidateAll();
        }
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

    private static void requireTrustedSkinUrl(String jsonPayload) {
        var marker = "\"url\"";
        int urlKey = jsonPayload.indexOf(marker);
        if (urlKey < 0) {
            throw new IllegalArgumentException("Parameter 'base64' must contain a textures.minecraft.net skin URL");
        }
        int colon = jsonPayload.indexOf(':', urlKey + marker.length());
        int quoteStart = jsonPayload.indexOf('"', colon + 1);
        int quoteEnd = quoteStart >= 0 ? jsonPayload.indexOf('"', quoteStart + 1) : -1;
        if (quoteStart < 0 || quoteEnd < 0) {
            throw new IllegalArgumentException("Parameter 'base64' must contain a textures.minecraft.net skin URL");
        }
        normalizeTextureUrl(jsonPayload.substring(quoteStart + 1, quoteEnd));
    }

    private static String toBase64Payload(String normalizedUrl) {
        var payload = "{\"textures\":{\"SKIN\":{\"url\":\"" + escapeJson(normalizedUrl) + "\"}}}";
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeTextureUrl(String input) {
        var stripped = input.strip();
        requireBoundedInput(stripped, "textureUrl", MAX_TEXTURE_URL_LENGTH);
        var lower = stripped.toLowerCase(Locale.ROOT);

        if (lower.startsWith(HTTP_TEXTURES_DOMAIN)) {
            return TEXTURES_DOMAIN + stripped.substring(HTTP_TEXTURES_DOMAIN.length());
        }
        if (lower.startsWith(TEXTURES_DOMAIN)) {
            return stripped;
        }
        if (lower.startsWith(BARE_DOMAIN)) {
            return "https://" + stripped;
        }

        if (stripped.contains("://") || stripped.contains(":") || stripped.contains("@") || stripped.startsWith("//")) {
            throw new IllegalArgumentException(
                    "Parameter 'textureUrl' contains an unsupported scheme or host: " + stripped);
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

    private static void requireBoundedInput(String value, String parameter, int maximumLength) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Parameter '" + parameter + "' must not be blank");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException("Parameter '" + parameter + "' exceeds maximum length " + maximumLength);
        }
    }
}
