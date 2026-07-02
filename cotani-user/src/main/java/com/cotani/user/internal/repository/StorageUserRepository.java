package com.cotani.user.internal.repository;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.user.internal.mapper.UserMapper;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class StorageUserRepository implements UserRepository {

    private static final String TABLE = "cotani_users";

    private final CotaniStorage storage;
    private final UserMapper mapper;

    public StorageUserRepository(CotaniStorage storage, UserMapper mapper) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
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
}
