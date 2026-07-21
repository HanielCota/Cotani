package com.cotani.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.net.URI;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SkullBuilder extends ItemStackBuilder<SkullBuilder> {

    private static final SkullTextureResolver TEXTURE_RESOLVER = new SkullTextureResolver();

    private SkullBuilder() {
        super(Material.PLAYER_HEAD);
    }

    public static SkullBuilder create() {
        return new SkullBuilder();
    }

    @Override
    protected SkullBuilder self() {
        return this;
    }

    public SkullBuilder player(Player player) {
        return player((OfflinePlayer) player);
    }

    public SkullBuilder player(OfflinePlayer player) {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        return profile(player.getPlayerProfile());
    }

    public SkullBuilder profile(PlayerProfile profile) {
        Objects.requireNonNull(profile, "Parameter 'profile' must not be null");
        item().setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(profile));
        return self();
    }

    public SkullBuilder removeProfile() {
        item().unsetData(DataComponentTypes.PROFILE);
        return self();
    }

    public SkullBuilder texture(String base64) {
        return profile(TEXTURE_RESOLVER.fromBase64(base64));
    }

    public SkullBuilder textureUrl(String textureUrl) {
        return profile(TEXTURE_RESOLVER.fromUrl(textureUrl));
    }

    public SkullBuilder textureUrl(URI textureUri) {
        return profile(TEXTURE_RESOLVER.fromUrl(textureUri));
    }

    public SkullBuilder noteBlockSound(NamespacedKey soundKey) {
        Objects.requireNonNull(soundKey, "Parameter 'soundKey' must not be null");
        item().setData(DataComponentTypes.NOTE_BLOCK_SOUND, soundKey);
        return self();
    }

    public SkullBuilder removeNoteBlockSound() {
        item().unsetData(DataComponentTypes.NOTE_BLOCK_SOUND);
        return self();
    }
}
