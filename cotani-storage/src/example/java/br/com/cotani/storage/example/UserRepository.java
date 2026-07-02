package br.com.cotani.storage.example;

import br.com.cotani.storage.api.CotaniStorage;
import br.com.cotani.storage.query.EntityMapper;
import br.com.cotani.storage.query.Row;
import br.com.cotani.storage.repository.PlayerDataRepository;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
    protected CompletionStage<User> create(UUID playerId, String name) {
        User user = new User(playerId, name, 0L);
        return save(user).thenApply(_ -> user);
    }

    @Override
    public CompletionStage<Void> save(User user) {
        return table("users")
                .upsert()
                .value("unique_id", user.uniqueId())
                .value("name", user.name())
                .value("coins", user.coins())
                .conflict("unique_id")
                .update("name", "coins")
                .execute();
    }

    public CompletionStage<Void> addCoins(UUID playerId, String name, long amount) {
        return findOrCreate(playerId, name)
                .thenApply(user -> user.addCoins(amount))
                .thenCompose(this::save);
    }

    private User map(Row row) throws SQLException {
        return new User(row.getUuid("unique_id"), row.getString("name"), row.getLong("coins"));
    }
}
