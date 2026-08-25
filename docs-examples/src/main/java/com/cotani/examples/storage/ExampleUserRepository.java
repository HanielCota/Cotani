package com.cotani.examples.storage;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.query.EntityMapper;
import com.cotani.storage.query.Row;
import com.cotani.storage.repository.PlayerDataRepository;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ExampleUserRepository extends PlayerDataRepository<ExampleUser> {
    private static final String TABLE = "users";
    private static final String UNIQUE_ID_COLUMN = "unique_id";
    private static final String NAME_COLUMN = "name";
    private static final String COINS_COLUMN = "coins";
    private final CotaniStorage storage;

    public ExampleUserRepository(CotaniStorage storage) {
        super(storage);
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    protected String tableName() {
        return TABLE;
    }

    @Override
    protected String idColumn() {
        return UNIQUE_ID_COLUMN;
    }

    @Override
    protected EntityMapper<ExampleUser> mapper() {
        return this::map;
    }

    @Override
    protected CompletionStage<ExampleUser> createAsync(UUID playerId, String name) {
        var user = new ExampleUser(playerId, name, 0L);
        return saveAsync(user).thenApply(_ -> user);
    }

    @Override
    protected CompletionStage<ExampleUser> create(UUID playerId, String name) {
        return createAsync(playerId, name);
    }

    @Override
    public CompletionStage<Void> saveAsync(ExampleUser user) {
        Objects.requireNonNull(user, "user");
        if (user.coins() < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("coins must not be negative"));
        }
        return table(TABLE)
                .upsert()
                .value(UNIQUE_ID_COLUMN, user.uniqueId())
                .value(NAME_COLUMN, user.name())
                .value(COINS_COLUMN, user.coins())
                .conflict(UNIQUE_ID_COLUMN)
                .update(NAME_COLUMN, COINS_COLUMN)
                .execute();
    }

    public CompletionStage<Void> addCoinsAsync(UUID playerId, String name, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(name, "name");
        if (amount <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("amount must be positive"));
        }

        return findOrCreateAsync(playerId, name)
                .thenCompose(_ -> storage.queryExecutor()
                        .update(
                                "UPDATE users SET coins = coins + ? WHERE unique_id = ? AND coins <= ?",
                                binder ->
                                        binder.longValue(amount).uuid(playerId).longValue(Long.MAX_VALUE - amount)));
    }

    private ExampleUser map(Row row) throws SQLException {
        return new ExampleUser(
                row.getUuidOptional(UNIQUE_ID_COLUMN)
                        .orElseThrow(() -> new IllegalStateException(UNIQUE_ID_COLUMN + " is SQL NULL")),
                row.getString(NAME_COLUMN),
                row.getLong(COINS_COLUMN));
    }
}
