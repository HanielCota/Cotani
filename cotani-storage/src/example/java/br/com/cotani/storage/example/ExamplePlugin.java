package br.com.cotani.storage.example;

import br.com.cotani.storage.api.CotaniStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {

    private CotaniStorage storage;
    private UserRepository users;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.storage = CotaniStorage.fromConfig(this, getConfig(), "storage")
            .migrations(new CreateUsersTableMigration())
            .repositories(UserRepository.class)
            .start();
        this.users = storage.repository(UserRepository.class);
    }

    @Override
    public void onDisable() {
        if (storage == null) {
            return;
        }

        storage.close();
    }

    public void addCoins(Player player, long amount) {
        users.addCoins(player, amount)
            .thenEntity(player, ignored -> player.sendMessage(Component.text("Coins adicionados.", NamedTextColor.GREEN)))
            .onFailureAsync(error -> getLogger().warning(error.getMessage()));
    }
}
