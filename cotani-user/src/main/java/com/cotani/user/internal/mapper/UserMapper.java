package com.cotani.user.internal.mapper;

import com.cotani.storage.query.Row;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.sql.SQLException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@com.cotani.api.InternalApi
public final class UserMapper {

    public SimpleCotaniUser toUser(Row row, UUID fallbackUniqueId, @Nullable String fallbackUsername, long now)
            throws SQLException {
        UUID uniqueId = row.getUuidOptional("unique_id").orElse(fallbackUniqueId);

        String username = row.getStringOptional("username")
                .filter(value -> !value.isBlank())
                .orElseGet(
                        () -> fallbackUsername != null && !fallbackUsername.isBlank() ? fallbackUsername : "unknown");

        long firstJoinAt = row.getLongOptional("first_join_at").orElse(now);
        long lastJoinAt = row.getLongOptional("last_join_at").orElse(now);
        long lastQuitAt = row.getLongOptional("last_quit_at").orElse(0L);
        long version = row.getLongOptional("version").orElse(0L);

        return new SimpleCotaniUser(
                uniqueId, UUID.randomUUID(), username, firstJoinAt, lastJoinAt, lastQuitAt, version);
    }
}
