package com.cotani.user.internal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.user.api.CotaniUser;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class SimpleCotaniUserTest {
    private static final UUID UNIQUE_ID = UUID.randomUUID();
    private static final long NOW = 1_000L;

    @Test
    void createNewAssignsDefaults() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        assertEquals(UNIQUE_ID, user.uniqueId());
        assertEquals("Steve", user.username());
        assertEquals(NOW, user.firstJoinAt());
        assertEquals(NOW, user.lastJoinAt());
        assertEquals(0L, user.lastQuitAt());
        assertEquals(0L, user.version());
        assertNotNull(user.sessionId());
    }

    @Test
    void createNewGeneratesDistinctSessionIdsPerCall() {
        SimpleCotaniUser first = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);
        SimpleCotaniUser second = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        assertNotEquals(first.sessionId(), second.sessionId());
    }

    @Test
    void constructorRejectsNullFields() {
        UUID sessionId = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> new SimpleCotaniUser(null, sessionId, "Steve", 1L, 1L, 1L, 1L));
        assertThrows(NullPointerException.class, () -> new SimpleCotaniUser(UNIQUE_ID, null, "Steve", 1L, 1L, 1L, 1L));
        assertThrows(
                NullPointerException.class, () -> new SimpleCotaniUser(UNIQUE_ID, sessionId, null, 1L, 1L, 1L, 1L));
    }

    @Test
    void withUsernameChangesOnlyUsername() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        SimpleCotaniUser updated = user.withUsername("Alex");

        assertNotSame(user, updated);
        assertEquals("Alex", updated.username());
        assertEquals(user.uniqueId(), updated.uniqueId());
        assertEquals(user.sessionId(), updated.sessionId());
        assertEquals(user.firstJoinAt(), updated.firstJoinAt());
        assertEquals(user.lastJoinAt(), updated.lastJoinAt());
        assertEquals(user.lastQuitAt(), updated.lastQuitAt());
        assertEquals(user.version(), updated.version());
    }

    @Test
    void withUsernameRejectsNull() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        assertThrows(NullPointerException.class, () -> user.withUsername(null));
    }

    @Test
    void withLastJoinAtChangesOnlyLastJoinAt() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        SimpleCotaniUser updated = user.withLastJoinAt(9_000L);

        assertEquals(9_000L, updated.lastJoinAt());
        assertEquals(NOW, updated.firstJoinAt());
        assertEquals(user.sessionId(), updated.sessionId());
        assertEquals(user.username(), updated.username());
        assertEquals(user.version(), updated.version());
    }

    @Test
    void withLastQuitAtChangesOnlyLastQuitAt() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        SimpleCotaniUser updated = user.withLastQuitAt(4_000L);

        assertEquals(4_000L, updated.lastQuitAt());
        assertEquals(0L, user.lastQuitAt());
        assertEquals(user.sessionId(), updated.sessionId());
    }

    @Test
    void withVersionChangesOnlyVersion() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        SimpleCotaniUser updated = user.withVersion(12L);

        assertEquals(12L, updated.version());
        assertEquals(user.sessionId(), updated.sessionId());
        assertEquals(user.username(), updated.username());
    }

    @Test
    void withIncrementedVersionAddsOne() {
        SimpleCotaniUser user =
                SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW).withVersion(4L);

        assertEquals(5L, user.withIncrementedVersion().version());
    }

    @Test
    void withNewSessionIdChangesOnlySessionId() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        SimpleCotaniUser updated = user.withNewSessionId();

        assertNotEquals(user.sessionId(), updated.sessionId());
        assertNotNull(updated.sessionId());
        assertEquals(user.uniqueId(), updated.uniqueId());
        assertEquals(user.username(), updated.username());
        assertEquals(user.version(), updated.version());
    }

    @Test
    void withSessionIdSetsExplicitSessionId() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);
        UUID sessionId = UUID.randomUUID();

        SimpleCotaniUser updated = user.withSessionId(sessionId);

        assertEquals(sessionId, updated.sessionId());
        assertEquals(user.username(), updated.username());
    }

    @Test
    void withSessionIdRejectsNull() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        assertThrows(NullPointerException.class, () -> user.withSessionId(null));
    }

    @Test
    void equalsAndHashCodeCompareAllFields() {
        UUID sessionId = UUID.randomUUID();
        SimpleCotaniUser first = new SimpleCotaniUser(UNIQUE_ID, sessionId, "Steve", 1L, 2L, 3L, 4L);
        SimpleCotaniUser same = new SimpleCotaniUser(UNIQUE_ID, sessionId, "Steve", 1L, 2L, 3L, 4L);
        SimpleCotaniUser differentName = new SimpleCotaniUser(UNIQUE_ID, sessionId, "Alex", 1L, 2L, 3L, 4L);
        SimpleCotaniUser differentVersion = new SimpleCotaniUser(UNIQUE_ID, sessionId, "Steve", 1L, 2L, 3L, 5L);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, differentName);
        assertNotEquals(first, differentVersion);
        assertNotEquals(first, null);
    }

    @Test
    void defaultInstantMethodsDeriveFromEpochMillis() {
        CotaniUser user = new SimpleCotaniUser(UNIQUE_ID, UUID.randomUUID(), "Steve", 1_000L, 2_000L, 3_000L, 0L);

        assertEquals(Instant.ofEpochMilli(1_000L), user.firstJoinInstant());
        assertEquals(Instant.ofEpochMilli(2_000L), user.lastJoinInstant());
        assertEquals(Instant.ofEpochMilli(3_000L), user.lastQuitInstant());
    }

    @Test
    void implementsCotaniUser() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UNIQUE_ID, "Steve", NOW);

        assertInstanceOf(CotaniUser.class, user);
    }
}
