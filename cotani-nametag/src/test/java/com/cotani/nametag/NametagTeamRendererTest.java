package com.cotani.nametag;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.nametag.api.CollisionRule;
import com.cotani.nametag.api.Nametag;
import com.cotani.nametag.api.NametagVisibility;
import com.cotani.nametag.internal.NametagTeamRenderer;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NametagTeamRendererTest {

    private Player viewer;
    private Player target;
    private Scoreboard scoreboard;
    private Team team;

    @BeforeEach
    void setUp() {
        viewer = mock(Player.class);
        target = mock(Player.class);
        scoreboard = mock(Scoreboard.class);
        team = mock(Team.class);

        var viewerId = UUID.randomUUID();
        var targetId = UUID.randomUUID();

        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.getName()).thenReturn("ViewerPlayer");
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getScoreboard()).thenReturn(scoreboard);

        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("TargetPlayer");
        when(target.isOnline()).thenReturn(true);

        when(scoreboard.getTeam(anyString())).thenReturn(team);
        when(scoreboard.registerNewTeam(anyString())).thenReturn(team);
        when(team.allowFriendlyFire()).thenReturn(true);
    }

    @Test
    void shouldRenderTeamOnScoreboard() {
        var nametag = Nametag.builder()
                .priority(5)
                .prefix(Component.text("[Owner] "))
                .suffix(Component.text(" [Staff]"))
                .color(NamedTextColor.DARK_RED)
                .visibility(NametagVisibility.ALWAYS)
                .collisionRule(CollisionRule.NEVER)
                .seeFriendlyInvisibles(true)
                .friendlyFire(false)
                .build();

        NametagTeamRenderer.renderTeam(viewer, target, nametag);

        verify(team).prefix(nametag.prefix());
        verify(team).suffix(nametag.suffix());
        verify(team).color(NamedTextColor.DARK_RED);
        verify(team).setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        verify(team).setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        verify(team).setCanSeeFriendlyInvisibles(true);
        verify(team).setAllowFriendlyFire(false);
        verify(team).addEntry("TargetPlayer");
    }

    @Test
    void shouldSkipRedundantUpdatesWhenPropertiesMatch() {
        var nametag = Nametag.builder()
                .priority(5)
                .prefix(Component.text("[VIP] "))
                .suffix(Component.text(" [Gold]"))
                .color(NamedTextColor.GOLD)
                .visibility(NametagVisibility.ALWAYS)
                .collisionRule(CollisionRule.ALWAYS)
                .seeFriendlyInvisibles(false)
                .friendlyFire(true)
                .build();

        // Stub team getters to return identical values
        when(team.prefix()).thenReturn(nametag.prefix());
        when(team.suffix()).thenReturn(nametag.suffix());
        when(team.color()).thenReturn(nametag.color());
        when(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).thenReturn(Team.OptionStatus.ALWAYS);
        when(team.getOption(Team.Option.COLLISION_RULE)).thenReturn(Team.OptionStatus.ALWAYS);
        when(team.canSeeFriendlyInvisibles()).thenReturn(false);
        when(team.allowFriendlyFire()).thenReturn(true);
        when(team.hasEntry("TargetPlayer")).thenReturn(true);

        NametagTeamRenderer.renderTeam(viewer, target, nametag);

        // Verify no setters were called since properties were already matching
        verify(team, org.mockito.Mockito.never()).prefix(org.mockito.ArgumentMatchers.any());
        verify(team, org.mockito.Mockito.never()).suffix(org.mockito.ArgumentMatchers.any());
        verify(team, org.mockito.Mockito.never()).color(org.mockito.ArgumentMatchers.any());
        verify(team, org.mockito.Mockito.never())
                .setOption(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(team, org.mockito.Mockito.never()).addEntry(anyString());
    }

    @Test
    void shouldRemoveTargetWhenRenderingEmptyNametag() {
        when(team.getName()).thenReturn("c_nt_0005_TargetPlayer");
        when(team.getEntries()).thenReturn(Collections.emptySet());
        when(scoreboard.getEntryTeam("TargetPlayer")).thenReturn(team);

        NametagTeamRenderer.renderTeam(viewer, target, Nametag.EMPTY);

        verify(team).removeEntry("TargetPlayer");
        verify(team).unregister();
    }

    @Test
    void shouldRemoveTargetAndUnregisterIfEmpty() {
        when(team.getName()).thenReturn("c_nt_0005_TargetPlayer");
        when(team.getEntries()).thenReturn(Collections.emptySet());
        when(scoreboard.getEntryTeam("TargetPlayer")).thenReturn(team);

        NametagTeamRenderer.removeTarget(viewer, "TargetPlayer");

        verify(team).removeEntry("TargetPlayer");
        verify(team).unregister();
    }

    @Test
    void shouldClearAllCotaniTeams() {
        var cotaniTeam = mock(Team.class);
        when(cotaniTeam.getName()).thenReturn("c_nt_0010_PlayerA");

        var otherTeam = mock(Team.class);
        when(otherTeam.getName()).thenReturn("other_plugin_team");

        when(scoreboard.getTeams()).thenReturn(Set.of(cotaniTeam, otherTeam));

        NametagTeamRenderer.clearAllTeams(viewer);

        verify(cotaniTeam).unregister();
    }

    @Test
    void shouldRemoveFromExternalTeamBeforeAddingToCotaniTeam() {
        var externalTeam = mock(Team.class);
        when(externalTeam.getName()).thenReturn("other_minigame_team");
        when(scoreboard.getEntryTeam("TargetPlayer")).thenReturn(externalTeam);

        var nametag = Nametag.of(Component.text("[Cotani] "), Component.empty(), 10);
        NametagTeamRenderer.renderTeam(viewer, target, nametag);

        verify(externalTeam).removeEntry("TargetPlayer");
        verify(team).addEntry("TargetPlayer");
    }
}
