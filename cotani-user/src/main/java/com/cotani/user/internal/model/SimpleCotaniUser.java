package com.cotani.user.internal.model;

import com.cotani.user.api.CotaniUser;
import java.util.Objects;
import java.util.UUID;

public record SimpleCotaniUser(
        UUID uniqueId,
        UUID sessionId,
        String username,
        long firstJoinAt,
        long lastJoinAt,
        long lastQuitAt,
        long version)
        implements CotaniUser {

    public SimpleCotaniUser {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(username, "username");
    }

    public static SimpleCotaniUser createNew(UUID uniqueId, String username, long now) {
        return new SimpleCotaniUser(uniqueId, UUID.randomUUID(), username, now, now, 0L, 0L);
    }

    public SimpleCotaniUser withUsername(String username) {
        return new SimpleCotaniUser(uniqueId, sessionId, username, firstJoinAt, lastJoinAt, lastQuitAt, version);
    }

    public SimpleCotaniUser withLastJoinAt(long lastJoinAt) {
        return new SimpleCotaniUser(uniqueId, sessionId, username, firstJoinAt, lastJoinAt, lastQuitAt, version);
    }

    public SimpleCotaniUser withLastQuitAt(long lastQuitAt) {
        return new SimpleCotaniUser(uniqueId, sessionId, username, firstJoinAt, lastJoinAt, lastQuitAt, version);
    }

    public SimpleCotaniUser withVersion(long version) {
        return new SimpleCotaniUser(uniqueId, sessionId, username, firstJoinAt, lastJoinAt, lastQuitAt, version);
    }

    public SimpleCotaniUser withIncrementedVersion() {
        return withVersion(version + 1);
    }
}
