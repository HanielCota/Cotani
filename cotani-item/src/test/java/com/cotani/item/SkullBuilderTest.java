package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.lang.reflect.Modifier;
import java.net.URI;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SkullBuilderTest {

    @Test
    void isFinalClassWithExpectedApi() throws NoSuchMethodException {
        assertTrue(Modifier.isFinal(SkullBuilder.class.getModifiers()));
        assertEquals(
                "com.cotani.item.ItemStackBuilder<com.cotani.item.SkullBuilder>",
                SkullBuilder.class.getGenericSuperclass().toString());

        SkullBuilder.class.getMethod("create");
        SkullBuilder.class.getMethod("player", Player.class);
        SkullBuilder.class.getMethod("player", OfflinePlayer.class);
        SkullBuilder.class.getMethod("profile", PlayerProfile.class);
        SkullBuilder.class.getMethod("removeProfile");
        SkullBuilder.class.getMethod("texture", String.class);
        SkullBuilder.class.getMethod("textureUrl", String.class);
        SkullBuilder.class.getMethod("textureUrl", URI.class);
        SkullBuilder.class.getMethod("noteBlockSound", NamespacedKey.class);
        SkullBuilder.class.getMethod("removeNoteBlockSound");
        SkullBuilder.class.getMethod("customName", Component.class);
        SkullBuilder.class.getMethod("customName", String.class);
        SkullBuilder.class.getMethod("build");
    }
}
