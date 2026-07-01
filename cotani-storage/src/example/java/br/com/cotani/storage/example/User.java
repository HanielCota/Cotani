package br.com.cotani.storage.example;

import java.util.UUID;
import org.bukkit.entity.Player;

public record User(UUID uniqueId, String name, long coins) {

    public static User create(Player player) {
        return new User(player.getUniqueId(), player.getName(), 0L);
    }

    public User addCoins(long amount) {
        return new User(uniqueId, name, coins + amount);
    }
}
