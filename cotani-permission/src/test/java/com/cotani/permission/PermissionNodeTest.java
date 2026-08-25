package com.cotani.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.permission.api.PermissionNode;
import org.junit.jupiter.api.Test;

class PermissionNodeTest {
    @Test
    void normalizesValuesAndMatchesDescendantsWithTrailingWildcard() {
        var wildcard = PermissionNode.of(" Server.MODERATION.* ");

        assertTrue(wildcard.matches(PermissionNode.of("server.moderation.kick")));
        assertTrue(wildcard.matches(PermissionNode.of("server.moderation.kick.silent")));
        assertFalse(wildcard.matches(PermissionNode.of("server.moderation")));
        assertFalse(wildcard.matches(PermissionNode.of("server.chat")));
    }

    @Test
    void rejectsMalformedNodes() {
        assertThrows(IllegalArgumentException.class, () -> PermissionNode.of("server.*.kick"));
        assertThrows(IllegalArgumentException.class, () -> PermissionNode.of("server..kick"));
        assertThrows(IllegalArgumentException.class, () -> PermissionNode.of("server.kick!"));
    }
}
