package br.com.cotani.storage.example;

import br.com.cotani.storage.api.CotaniStorage;
import java.util.UUID;
import java.util.logging.Level;
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
        CotaniStorage.fromConfig(this, getConfig(), "storage")
                .migrations(new CreateUsersTableMigration())
                .repositories(UserRepository.class)
                .build()
                .startAsync()
                .whenComplete((started, error) -> {
                    if (error != null) {
                        getLogger().log(Level.SEVERE, "Could not start storage.", error);
                        return;
                    }
                    this.storage = started;
                    this.users = started.repository(UserRepository.class);
                });
    }

    @Override
    public void onDisable() {
        if (storage == null) {
            return;
        }

        storage.close();
    }

    public void addCoins(Player player, long amount) {
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        users.addCoins(playerId, name, amount)
                .whenCompleteAsync((ignored, error) -> {
                    if (error != null) {
                        getLogger().warning(error.getMessage());
                        return;
                    }
                    player.sendMessage(Component.text("Coins adicionados.", NamedTextColor.GREEN));
                }, storage.scheduler().globalExecutor());
    }
}
