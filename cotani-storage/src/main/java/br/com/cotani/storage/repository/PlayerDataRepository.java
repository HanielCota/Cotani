package br.com.cotani.storage.repository;

import br.com.cotani.storage.api.CotaniStorage;
import br.com.cotani.storage.future.StorageFuture;
import java.util.UUID;
import org.bukkit.entity.Player;

public abstract class PlayerDataRepository<T> extends CrudRepository<UUID, T> {

    protected PlayerDataRepository(CotaniStorage storage) {
        super(storage);
    }

    public StorageFuture<T> findOrCreate(Player player) {
        return findById(player.getUniqueId()).fallbackFuture(() -> create(player));
    }

    protected abstract StorageFuture<T> create(Player player);
}
