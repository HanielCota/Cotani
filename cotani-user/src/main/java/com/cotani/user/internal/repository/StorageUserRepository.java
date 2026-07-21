package com.cotani.user.internal.repository;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.SqlConsumer;
import com.cotani.task.util.CompletionStages;
import com.cotani.user.internal.mapper.UserMapper;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.util.*;
import java.util.concurrent.CompletionStage;

public final class StorageUserRepository implements UserRepository {

    private static final String TABLE = "cotani_users";
    private static final List<String> UPSERT_COLUMNS =
            List.of("unique_id", "username", "first_join_at", "last_join_at", "last_quit_at", "version");
    private static final List<String> CONFLICT_COLUMNS = List.of("unique_id");
    private static final List<String> UPDATE_COLUMNS = List.of("username", "last_join_at", "last_quit_at", "version");

    private final CotaniStorage storage;
    private final UserMapper mapper;

    public StorageUserRepository(CotaniStorage storage, UserMapper mapper) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    private static SqlConsumer<ParameterBinder> bindUser(SimpleCotaniUser user) {
        return binder -> {
            binder.set(user.uniqueId());
            binder.set(user.username());
            binder.set(user.firstJoinAt());
            binder.set(user.lastJoinAt());
            binder.set(user.lastQuitAt());
            binder.set(user.version());
        };
    }

    @Override
    public CompletionStage<Optional<SimpleCotaniUser>> find(UUID uniqueId, String username) {
        long now = System.currentTimeMillis();

        return storage.table(TABLE)
                .select()
                .where("unique_id", uniqueId)
                .limit(1)
                .one(row -> mapper.toUser(row, uniqueId, username, now));
    }

    @Override
    public CompletionStage<Optional<SimpleCotaniUser>> findByUniqueId(UUID uniqueId) {
        return find(uniqueId, "unknown");
    }

    @Override
    public CompletionStage<Void> save(SimpleCotaniUser user) {
        return storage.table(TABLE)
                .upsert()
                .value("unique_id", user.uniqueId())
                .value("username", user.username())
                .value("first_join_at", user.firstJoinAt())
                .value("last_join_at", user.lastJoinAt())
                .value("last_quit_at", user.lastQuitAt())
                .value("version", user.version())
                .conflict("unique_id")
                .update("username", "last_join_at", "last_quit_at", "version")
                .execute();
    }

    @Override
    public CompletionStage<Void> saveAll(Collection<SimpleCotaniUser> users) {
        if (users.isEmpty()) {
            return CompletionStages.completedVoid();
        }

        String sql = storage.dialect().upsert(TABLE, UPSERT_COLUMNS, CONFLICT_COLUMNS, UPDATE_COLUMNS);
        List<SqlConsumer<ParameterBinder>> binders =
                users.stream().map(StorageUserRepository::bindUser).toList();

        return storage.transactions().run(tx -> tx.batch(sql, binders));
    }
}
