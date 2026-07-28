package com.cotani.user.internal.mapper;

import com.cotani.storage.query.Row;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class UserMapper {

    public SimpleCotaniUser toUser(Row row, UUID fallbackUniqueId, @Nullable String fallbackUsername, long now)
            throws SQLException {
        UUID uniqueId = Objects.requireNonNullElse(row.getUuid("unique_id"), fallbackUniqueId);

        String rawUsername = row.getString("username");
        String username = (rawUsername != null && !rawUsername.isBlank())
                ? rawUsername
                : (fallbackUsername != null && !fallbackUsername.isBlank() ? fallbackUsername : "unknown");

        long firstJoinAt = Objects.requireNonNullElse(row.getLongOrNull("first_join_at"), now);
        long lastJoinAt = Objects.requireNonNullElse(row.getLongOrNull("last_join_at"), now);
        long lastQuitAt = Objects.requireNonNullElse(row.getLongOrNull("last_quit_at"), 0L);
        long version = Objects.requireNonNullElse(row.getLongOrNull("version"), 0L);

        return new SimpleCotaniUser(
                uniqueId, UUID.randomUUID(), username, firstJoinAt, lastJoinAt, lastQuitAt, version);
    }
}
