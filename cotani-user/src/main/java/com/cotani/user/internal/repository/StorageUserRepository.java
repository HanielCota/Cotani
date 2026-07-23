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

    private static final String UNIQUE_ID_COL = "unique_id";
    private static final String USERNAME_COL = "username";
    private static final String LAST_JOIN_AT_COL = "last_join_at";
    private static final String LAST_QUIT_AT_COL = "last_quit_at";
    private static final String VERSION_COL = "version";

    private static final String TABLE = "cotani_users";
    private static final List<String> UPSERT_COLUMNS =
            List.of(UNIQUE_ID_COL, USERNAME_COL, "first_join_at", LAST_JOIN_AT_COL, LAST_QUIT_AT_COL, VERSION_COL);
    private static final List<String> CONFLICT_COLUMNS = List.of(UNIQUE_ID_COL);
    private static final List<String> UPDATE_COLUMNS = List.of(USERNAME_COL, LAST_JOIN_AT_COL, LAST_QUIT_AT_COL, VERSION_COL);

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
                .where(UNIQUE_ID_COL, uniqueId)
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
                .value(UNIQUE_ID_COL, user.uniqueId())
                .value(USERNAME_COL, user.username())
                .value("first_join_at", user.firstJoinAt())
                .value(LAST_JOIN_AT_COL, user.lastJoinAt())
                .value(LAST_QUIT_AT_COL, user.lastQuitAt())
                .value(VERSION_COL, user.version())
                .conflict(UNIQUE_ID_COL)
                .update(USERNAME_COL, LAST_JOIN_AT_COL, LAST_QUIT_AT_COL, VERSION_COL)
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
