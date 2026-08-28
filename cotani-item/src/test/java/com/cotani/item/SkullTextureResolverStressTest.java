package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.testkit.StressTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@Tag("stress")
class SkullTextureResolverStressTest {
    private static final Material[] ITEM_MATERIALS = {
        Material.DIAMOND, Material.STONE, Material.PAPER, Material.NETHER_STAR
    };
    private static final Material[] ARMOR_MATERIALS = {
        Material.IRON_HELMET, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.LEATHER_BOOTS
    };

    @Test
    void buildsOneThousandVariedItemsWithoutSharingMutableStacks() {
        try (MockedStatic<ItemStack> items = mockStatic(ItemStack.class)) {
            StressTestSupport.scenarios("item", "fluent-item-build", (context, random, player) -> {
                var material = ITEM_MATERIALS[context.iteration() % ITEM_MATERIALS.length];
                var amount = random.nextInt(1, 65);
                var source = mock(ItemStack.class);
                var built = mock(ItemStack.class);
                items.when(() -> ItemStack.of(material)).thenReturn(source);
                when(source.clone()).thenReturn(built);

                var builder = ItemBuilder.of(material);
                assertSame(builder, builder.amount(amount), context::description);
                assertSame(builder, builder.flags(ItemFlag.HIDE_ATTRIBUTES), context::description);
                assertSame(built, builder.build(), context::description);

                verify(source).setAmount(amount);
                verify(source).addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                verify(source).clone();
            });
        }
    }

    @Test
    void buildsOneThousandVariedArmorItemsWithoutSharingMutableStacks() {
        try (MockedStatic<ItemStack> items = mockStatic(ItemStack.class)) {
            StressTestSupport.scenarios("item", "fluent-armor-build", (context, random, player) -> {
                var material = ARMOR_MATERIALS[context.iteration() % ARMOR_MATERIALS.length];
                var amount = random.nextInt(1, 5);
                var source = mock(ItemStack.class);
                var built = mock(ItemStack.class);
                items.when(() -> ItemStack.of(material)).thenReturn(source);
                when(source.clone()).thenReturn(built);

                var builder = ArmorBuilder.of(material);
                assertSame(builder, builder.amount(amount), context::description);
                assertSame(built, builder.build(), context::description);

                verify(source).setAmount(amount);
                verify(source).clone();
            });
        }
    }

    @Test
    void rejectsOneThousandUntrustedTextureHostsBeforePaperProfileCreation() {
        try (var resolver = SkullTextureResolver.uncached()) {
            StressTestSupport.scenarios("item", "untrusted-texture-host", (context, random, player) -> {
                var scheme = context.iteration() % 2 == 0 ? "https" : "http";
                var textureUrl =
                        scheme + "://cdn-" + random.nextInt(1, 10_000) + ".example.invalid/texture/" + player.id();

                assertThrows(IllegalArgumentException.class, () -> resolver.fromUrl(textureUrl), context::description);
            });
        }
    }

    @Test
    void rejectsOneThousandBase64PayloadsThatAttemptHostInjection() {
        try (var resolver = SkullTextureResolver.uncached()) {
            StressTestSupport.scenarios("item", "base64-host-injection", (context, random, player) -> {
                var textureUrl = "https://textures.minecraft.net@attacker-" + random.nextInt(1, 10_000)
                        + ".example.invalid/texture/" + player.id();
                var payload = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
                var base64 = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

                assertThrows(IllegalArgumentException.class, () -> resolver.fromBase64(base64), context::description);
            });
        }
    }
}
