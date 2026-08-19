package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.net.URI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@SuppressWarnings({"NullAway", "removal", "try"})
class SkullBuilderBehaviorTest {
    private static final String BASE64 = "aGVsbG8=";

    private static void withHeadItem(TestBody body) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                MockedStatic<ItemStack> items = mockStatic(ItemStack.class)) {
            ItemStack stack = mock(ItemStack.class);
            when(ItemStack.of(Material.PLAYER_HEAD)).thenReturn(stack);
            body.run(stack);
        }
    }

    @Test
    void createReturnsBuilderBackedByPlayerHead() {
        withHeadItem(stack -> {
            SkullBuilder builder = SkullBuilder.create();

            assertNotNull(builder);
        });
    }

    @Test
    void createRejectsNullResolver() {
        withHeadItem(stack -> {
            assertThrows(NullPointerException.class, () -> SkullBuilder.create(null));
        });
    }

    @Test
    void playerAndProfileRejectNull() {
        withHeadItem(stack -> {
            SkullBuilder builder = SkullBuilder.create(mock(SkullTextureResolver.class));

            assertThrows(NullPointerException.class, () -> builder.player((Player) null));
            assertThrows(NullPointerException.class, () -> builder.player((OfflinePlayer) null));
            assertThrows(NullPointerException.class, () -> builder.profile(null));
            assertThrows(NullPointerException.class, () -> builder.noteBlockSound((NamespacedKey) null));
        });
    }

    @Test
    void textureDelegatesToResolverBeforeApplyingProfile() {
        withHeadItem(stack -> {
            SkullTextureResolver resolver = mock(SkullTextureResolver.class);
            when(resolver.fromBase64(BASE64)).thenReturn(mock(PlayerProfile.class));
            SkullBuilder builder = SkullBuilder.create(resolver);

            assertThrows(Throwable.class, () -> builder.texture(BASE64));

            verify(resolver).fromBase64(BASE64);
        });
    }

    @Test
    void textureUrlDelegatesToResolverBeforeApplyingProfile() {
        withHeadItem(stack -> {
            SkullTextureResolver resolver = mock(SkullTextureResolver.class);
            when(resolver.fromUrl("https://textures.minecraft.net/texture/abc")).thenReturn(mock(PlayerProfile.class));
            SkullBuilder builder = SkullBuilder.create(resolver);

            assertThrows(Throwable.class, () -> builder.textureUrl("https://textures.minecraft.net/texture/abc"));

            verify(resolver).fromUrl("https://textures.minecraft.net/texture/abc");
        });
    }

    @Test
    void textureUriDelegatesToResolverBeforeApplyingProfile() {
        withHeadItem(stack -> {
            SkullTextureResolver resolver = mock(SkullTextureResolver.class);
            URI uri = URI.create("https://textures.minecraft.net/texture/abc");
            when(resolver.fromUrl(uri)).thenReturn(mock(PlayerProfile.class));
            SkullBuilder builder = SkullBuilder.create(resolver);

            assertThrows(Throwable.class, () -> builder.textureUrl(uri));

            verify(resolver).fromUrl(uri);
        });
    }

    @Test
    void playerUsesOfflinePlayerProfile() {
        withHeadItem(stack -> {
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            when(offlinePlayer.getPlayerProfile()).thenReturn(mock(PlayerProfile.class));
            SkullBuilder builder = SkullBuilder.create(mock(SkullTextureResolver.class));

            assertThrows(Throwable.class, () -> builder.player(offlinePlayer));

            verify(offlinePlayer).getPlayerProfile();
        });
    }

    @Test
    void buildReturnsClonedItem() {
        withHeadItem(stack -> {
            ItemStack clone = mock(ItemStack.class);
            when(stack.clone()).thenReturn(clone);
            SkullBuilder builder = SkullBuilder.create();

            assertSame(clone, builder.build());
            verify(stack).clone();
        });
    }

    private interface TestBody {
        void run(ItemStack stack);
    }
}
