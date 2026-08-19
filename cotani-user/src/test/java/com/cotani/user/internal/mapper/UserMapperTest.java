package com.cotani.user.internal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.storage.query.Row;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class UserMapperTest {
    private final Row row = mock(Row.class);
    private final UserMapper mapper = new UserMapper();

    @Test
    void toUserMapsAllColumnsToDomainValues() throws SQLException {
        UUID uniqueId = UUID.randomUUID();
        when(row.getUuidOptional("unique_id")).thenReturn(Optional.of(uniqueId));
        when(row.getStringOptional("username")).thenReturn(Optional.of("Steve"));
        when(row.getLongOptional("first_join_at")).thenReturn(Optional.of(1_000L));
        when(row.getLongOptional("last_join_at")).thenReturn(Optional.of(2_000L));
        when(row.getLongOptional("last_quit_at")).thenReturn(Optional.of(3_000L));
        when(row.getLongOptional("version")).thenReturn(Optional.of(7L));

        SimpleCotaniUser user = mapper.toUser(row, uniqueId, "Fallback", 5_000L);

        assertEquals(uniqueId, user.uniqueId());
        assertEquals("Steve", user.username());
        assertEquals(1_000L, user.firstJoinAt());
        assertEquals(2_000L, user.lastJoinAt());
        assertEquals(3_000L, user.lastQuitAt());
        assertEquals(7L, user.version());
    }

    @Test
    void toUserUsesFallbackUniqueIdWhenColumnIsMissing() throws SQLException {
        UUID fallback = UUID.randomUUID();
        when(row.getUuidOptional("unique_id")).thenReturn(Optional.empty());
        when(row.getStringOptional("username")).thenReturn(Optional.of("Steve"));

        SimpleCotaniUser user = mapper.toUser(row, fallback, "Steve", 1_000L);

        assertEquals(fallback, user.uniqueId());
    }

    @Test
    void toUserFallsBackToNowAndUnknownWhenRowValuesAreMissing() throws SQLException {
        UUID uniqueId = UUID.randomUUID();
        when(row.getUuidOptional("unique_id")).thenReturn(Optional.empty());
        when(row.getStringOptional("username")).thenReturn(Optional.empty());
        when(row.getLongOptional("first_join_at")).thenReturn(Optional.empty());
        when(row.getLongOptional("last_join_at")).thenReturn(Optional.empty());
        when(row.getLongOptional("last_quit_at")).thenReturn(Optional.empty());
        when(row.getLongOptional("version")).thenReturn(Optional.empty());

        SimpleCotaniUser user = mapper.toUser(row, uniqueId, null, 5_000L);

        assertEquals("unknown", user.username());
        assertEquals(5_000L, user.firstJoinAt());
        assertEquals(5_000L, user.lastJoinAt());
        assertEquals(0L, user.lastQuitAt());
        assertEquals(0L, user.version());
    }

    @Test
    void toUserUsesFallbackUsernameWhenStoredUsernameIsBlank() throws SQLException {
        UUID uniqueId = UUID.randomUUID();
        when(row.getUuidOptional("unique_id")).thenReturn(Optional.of(uniqueId));
        when(row.getStringOptional("username")).thenReturn(Optional.of("   "));

        SimpleCotaniUser user = mapper.toUser(row, uniqueId, "Steve", 1_000L);

        assertEquals("Steve", user.username());
    }

    @Test
    void toUserFallsBackToUnknownWhenRowAndFallbackUsernamesAreBlank() throws SQLException {
        UUID uniqueId = UUID.randomUUID();
        when(row.getUuidOptional("unique_id")).thenReturn(Optional.of(uniqueId));
        when(row.getStringOptional("username")).thenReturn(Optional.of(" "));

        SimpleCotaniUser user = mapper.toUser(row, uniqueId, " ", 1_000L);

        assertEquals("unknown", user.username());
    }

    @Test
    void toUserGeneratesDistinctSessionIdsPerConversion() throws SQLException {
        UUID uniqueId = UUID.randomUUID();
        when(row.getUuidOptional("unique_id")).thenReturn(Optional.of(uniqueId));
        when(row.getStringOptional("username")).thenReturn(Optional.of("Steve"));

        SimpleCotaniUser first = mapper.toUser(row, uniqueId, "Steve", 1_000L);
        SimpleCotaniUser second = mapper.toUser(row, uniqueId, "Steve", 1_000L);

        assertNotNull(first.sessionId());
        assertNotEquals(first.sessionId(), second.sessionId());
    }

    @Test
    void toUserPropagatesSqlExceptionFromRow() throws SQLException {
        when(row.getUuidOptional("unique_id")).thenThrow(new SQLException("boom"));

        assertThrows(SQLException.class, () -> mapper.toUser(row, UUID.randomUUID(), "Steve", 1_000L));
    }

    @Test
    void toUserRejectsNullRow() {
        assertThrows(NullPointerException.class, () -> mapper.toUser(null, UUID.randomUUID(), "Steve", 1_000L));
    }

    @Test
    void toUserRejectsNullFallbackUniqueIdWhenColumnIsMissing() throws SQLException {
        when(row.getUuidOptional("unique_id")).thenReturn(Optional.empty());

        assertThrows(NullPointerException.class, () -> mapper.toUser(row, null, "Steve", 1_000L));
    }
}
