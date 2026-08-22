package com.cotani.command.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PermissionRequirementTest {

    @Test
    void shouldAlwaysPermitWhenRequirementIsNone() {
        var req = PermissionRequirement.none();
        var sender = mock(CommandSender.class);

        assertTrue(req.test(sender));
        assertTrue(req.node().isEmpty());
    }

    @Test
    void shouldCheckBukkitPermissionNode() {
        var req = PermissionRequirement.of("cotani.admin.ban");
        assertEquals("cotani.admin.ban", req.node().orElseThrow());

        var player = mock(Player.class);
        when(player.hasPermission("cotani.admin.ban")).thenReturn(true);
        when(player.hasPermission("other.perm")).thenReturn(false);

        assertTrue(req.test(player));

        var regularUser = mock(Player.class);
        when(regularUser.hasPermission("cotani.admin.ban")).thenReturn(false);

        assertFalse(req.test(regularUser));
    }

    @Test
    void shouldEvaluateCustomPredicate() {
        var req = PermissionRequirement.predicate(sender -> sender instanceof ConsoleCommandSender || sender.isOp());
        assertTrue(req.node().isEmpty());

        var console = mock(ConsoleCommandSender.class);
        assertTrue(req.test(console));

        var opPlayer = mock(Player.class);
        when(opPlayer.isOp()).thenReturn(true);
        assertTrue(req.test(opPlayer));

        var regularPlayer = mock(Player.class);
        when(regularPlayer.isOp()).thenReturn(false);
        assertFalse(req.test(regularPlayer));
    }
}
