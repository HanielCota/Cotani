package br.com.cotani.storage.example;

import br.com.cotani.storage.api.CotaniStorage;
import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.query.EntityMapper;
import br.com.cotani.storage.query.Row;
import br.com.cotani.storage.repository.PlayerDataRepository;
import java.sql.SQLException;
import org.bukkit.entity.Player;

public final class UserRepository extends PlayerDataRepository<User> {

    public UserRepository(CotaniStorage storage) {
        super(storage);
    }

    @Override
    protected String tableName() {
        return "users";
    }

    @Override
    protected String idColumn() {
        return "unique_id";
    }

    @Override
    protected EntityMapper<User> mapper() {
        return this::map;
    }

    @Override
    protected StorageFuture<User> create(Player player) {
        User user = User.create(player);
        return save(user).map(ignored -> user);
    }

    @Override
    public StorageFuture<Void> save(User user) {
        return table("users")
            .upsert()
            .value("unique_id", user.uniqueId())
            .value("name", user.name())
            .value("coins", user.coins())
            .conflict("unique_id")
            .update("name", "coins")
            .execute();
    }

    public StorageFuture<Void> addCoins(Player player, long amount) {
        return findOrCreate(player)
            .map(user -> user.addCoins(amount))
            .flatMap(this::save);
    }

    private User map(Row row) throws SQLException {
        return new User(
            row.getUuid("unique_id"),
            row.getString("name"),
            row.getLong("coins")
        );
    }
}
