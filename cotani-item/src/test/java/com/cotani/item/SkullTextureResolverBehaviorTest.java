package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@SuppressWarnings({"NullAway", "removal"})
class SkullTextureResolverBehaviorTest {
    private static final String BASE64 = "aGVsbG8=";
    private static final String TEXTURE_ID = "abc123";
    private static final String FULL_URL = "https://textures.minecraft.net/texture/abc123";

    private static PlayerProfile givenProfile(MockedStatic<Bukkit> bukkit) {
        PlayerProfile profile = mock(PlayerProfile.class);
        bukkit.when(() -> Bukkit.createProfile(any(UUID.class))).thenReturn(profile);

        return profile;
    }

    private static ProfileProperty capturedProperty(PlayerProfile profile) {
        ArgumentCaptor<ProfileProperty> property = ArgumentCaptor.forClass(ProfileProperty.class);
        verify(profile).setProperty(property.capture());

        return property.getValue();
    }

    @Test
    void fromBase64BuildsProfileWithTexturesProperty() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PlayerProfile profile = givenProfile(bukkit);
            try (var resolver = new SkullTextureResolver()) {
                PlayerProfile result = resolver.fromBase64(BASE64);

                assertSame(profile, result);
                ProfileProperty property = capturedProperty(profile);
                assertEquals("textures", property.getName());
                assertEquals(BASE64, property.getValue());
            }
        }
    }

    @Test
    void fromBase64UsesDeterministicUuidFromPayload() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            givenProfile(bukkit);
            try (var resolver = new SkullTextureResolver()) {
                resolver.fromBase64(BASE64);
            }

            ArgumentCaptor<UUID> uuid = ArgumentCaptor.forClass(UUID.class);
            bukkit.verify(() -> Bukkit.createProfile(uuid.capture()));
            assertEquals(UUID.nameUUIDFromBytes(BASE64.getBytes(StandardCharsets.UTF_8)), uuid.getValue());
        }
    }

    @Test
    void fromBase64CachesProfilePerInput() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            givenProfile(bukkit);
            try (var resolver = new SkullTextureResolver()) {
                PlayerProfile first = resolver.fromBase64(BASE64);
                PlayerProfile second = resolver.fromBase64(BASE64);

                assertSame(first, second);
                bukkit.verify(() -> Bukkit.createProfile(any(UUID.class)), times(1));
            }
        }
    }

    @Test
    void fromBase64CreatesDistinctProfilesForDistinctInputs() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PlayerProfile firstProfile = mock(PlayerProfile.class);
            PlayerProfile secondProfile = mock(PlayerProfile.class);
            bukkit.when(() -> Bukkit.createProfile(any(UUID.class))).thenReturn(firstProfile, secondProfile);
            try (var resolver = new SkullTextureResolver()) {
                PlayerProfile first = resolver.fromBase64(BASE64);
                PlayerProfile second = resolver.fromBase64("c2Vjb25k");

                assertNotSame(first, second);
                assertSame(firstProfile, first);
                assertSame(secondProfile, second);
            }
        }
    }

    @Test
    void fromUrlNormalizesHttpToHttpsAndSharesCacheEntry() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            givenProfile(bukkit);
            try (var resolver = new SkullTextureResolver()) {
                PlayerProfile http = resolver.fromUrl("http://textures.minecraft.net/texture/abc123");
                PlayerProfile https = resolver.fromUrl(FULL_URL);

                assertSame(http, https);
                bukkit.verify(() -> Bukkit.createProfile(any(UUID.class)), times(1));
            }
        }
    }

    @Test
    void fromUrlBareDomainAndTextureIdShareCacheEntry() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            givenProfile(bukkit);
            try (var resolver = new SkullTextureResolver()) {
                PlayerProfile bare = resolver.fromUrl("textures.minecraft.net/texture/abc123");
                PlayerProfile idOnly = resolver.fromUrl(TEXTURE_ID);
                PlayerProfile full = resolver.fromUrl(FULL_URL);

                assertSame(bare, idOnly);
                assertSame(idOnly, full);
                bukkit.verify(() -> Bukkit.createProfile(any(UUID.class)), times(1));
            }
        }
    }

    @Test
    void fromUrlBuildsEscapedJsonPayload() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PlayerProfile profile = givenProfile(bukkit);
            String url = "https://textures.minecraft.net/texture/a\"b\\c";
            try (var resolver = new SkullTextureResolver()) {
                resolver.fromUrl(url);
            }

            ProfileProperty property = capturedProperty(profile);
            String payload = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
            assertEquals(
                    "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/a\\\"b\\\\c\"}}}",
                    payload);
        }
    }

    @Test
    void fromUrlRejectsBlankAndOversized() {
        try (var resolver = new SkullTextureResolver()) {
            assertThrows(IllegalArgumentException.class, () -> resolver.fromUrl("   "));
            assertThrows(IllegalArgumentException.class, () -> resolver.fromUrl("x".repeat(2_049)));
        }
    }

    @Test
    void fromBase64RejectsBlankInput() {
        try (var resolver = new SkullTextureResolver()) {
            assertThrows(IllegalArgumentException.class, () -> resolver.fromBase64("  "));
        }
    }

    @Test
    void publicApiRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> new SkullTextureResolver(null));
        try (var resolver = new SkullTextureResolver()) {
            assertThrows(NullPointerException.class, () -> resolver.fromBase64(null));
            assertThrows(NullPointerException.class, () -> resolver.fromUrl((String) null));
            assertThrows(NullPointerException.class, () -> resolver.fromUrl((URI) null));
        }
    }

    @Test
    void clearCacheInvalidatesCachedProfiles() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            givenProfile(bukkit);
            try (var resolver = new SkullTextureResolver()) {
                resolver.fromBase64(BASE64);
                resolver.clearCache();
                resolver.fromBase64(BASE64);

                bukkit.verify(() -> Bukkit.createProfile(any(UUID.class)), times(2));
            }
        }
    }

    @Test
    void closeInvalidatesCachedProfiles() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            givenProfile(bukkit);
            var resolver = new SkullTextureResolver();
            resolver.fromBase64(BASE64);
            resolver.close();
            resolver.fromBase64(BASE64);

            bukkit.verify(() -> Bukkit.createProfile(any(UUID.class)), times(2));
        }
    }

    @Test
    void uncachedResolverDoesNotRetainProfiles() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PlayerProfile firstProfile = mock(PlayerProfile.class);
            PlayerProfile secondProfile = mock(PlayerProfile.class);
            bukkit.when(() -> Bukkit.createProfile(any(UUID.class))).thenReturn(firstProfile, secondProfile);
            try (var resolver = SkullTextureResolver.uncached()) {
                PlayerProfile first = resolver.fromBase64(BASE64);
                PlayerProfile second = resolver.fromBase64(BASE64);

                assertNotSame(first, second);
                assertSame(firstProfile, first);
                assertSame(secondProfile, second);
            }
        }
    }
}
