package com.cotani.examples.storage;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.query.EntityMapper;
import com.cotani.storage.query.Row;
import com.cotani.storage.repository.PlayerDataRepository;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class ExampleUserRepository extends PlayerDataRepository<ExampleUser> {

    public ExampleUserRepository(CotaniStorage storage) {
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
    protected EntityMapper<ExampleUser> mapper() {
        return this::map;
    }

    @Override
    protected CompletionStage<ExampleUser> create(UUID playerId, String name) {
        var user = new ExampleUser(playerId, name, 0L);
        return save(user).thenApply(_ -> user);
    }

    @Override
    public CompletionStage<Void> save(ExampleUser user) {
        return table("users")
                .upsert()
                .value("unique_id", user.uniqueId())
                .value("name", user.name())
                .value("coins", user.coins())
                .conflict("unique_id")
                .update("name", "coins")
                .execute();
    }

    public CompletionStage<Void> addCoinsAsync(UUID playerId, String name, long amount) {
        return findOrCreate(playerId, name)
                .thenApply(user -> user.addCoins(amount))
                .thenCompose(this::save);
    }

    private ExampleUser map(Row row) throws SQLException {
        return new ExampleUser(
                row.getUuidOptional("unique_id").orElseThrow(() -> new IllegalStateException("unique_id is SQL NULL")),
                row.getString("name"),
                row.getLong("coins"));
    }
}
