package com.cotani.user.internal.mapper;

import com.cotani.storage.query.Row;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.sql.SQLException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class UserMapper {

    public SimpleCotaniUser toUser(Row row, UUID fallbackUniqueId, @Nullable String fallbackUsername, long now)
            throws SQLException {
        UUID uniqueId = row.getUuid("unique_id");
        if (uniqueId == null) {
            uniqueId = fallbackUniqueId;
        }

        String username = row.getString("username");
        if (username.isBlank()) {
            username = fallbackUsername == null || fallbackUsername.isBlank() ? "unknown" : fallbackUsername;
        }

        Long firstJoinAt = row.getLongOrNull("first_join_at");
        Long lastJoinAt = row.getLongOrNull("last_join_at");
        Long lastQuitAt = row.getLongOrNull("last_quit_at");
        Long version = row.getLongOrNull("version");

        return new SimpleCotaniUser(
                uniqueId,
                UUID.randomUUID(),
                username,
                firstJoinAt == null ? now : firstJoinAt,
                lastJoinAt == null ? now : lastJoinAt,
                lastQuitAt == null ? 0L : lastQuitAt,
                version == null ? 0L : version);
    }
}
