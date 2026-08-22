package com.cotani.nametag.impl;

import com.cotani.api.InternalApi;
import com.cotani.nametag.api.Nametag;
import java.util.ArrayList;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

/**
 * Low-level, zero-flicker Scoreboard Team renderer for Cotani nametags.
 *
 * <p>Must be invoked on the viewer's entity/region thread.
 */
@InternalApi
public final class NametagTeamRenderer {

    private static final String TEAM_PREFIX = "c_nt_";
    private static final int MAX_PRIORITY = 9999;

    private NametagTeamRenderer() {}

    /**
     * Renders or updates the given target's nametag team on the viewer's active scoreboard.
     *
     * @param viewer the viewer observing the scoreboard
     * @param target the target player to place into a team
     * @param nametag the nametag formatting and team options to apply
     */
    public static void renderTeam(Player viewer, Player target, Nametag nametag) {
        Objects.requireNonNull(viewer, "Parameter 'viewer' must not be null");
        Objects.requireNonNull(target, "Parameter 'target' must not be null");
        Objects.requireNonNull(nametag, "Parameter 'nametag' must not be null");

        if (!viewer.isOnline() || !target.isOnline()) {
            return;
        }

        var targetEntry = target.getName();
        if (nametag.equals(Nametag.EMPTY)) {
            removeTarget(viewer, targetEntry);
            return;
        }

        var board = viewer.getScoreboard();
        var teamName = buildTeamName(nametag.priority(), targetEntry);

        var currentTeam = board.getEntryTeam(targetEntry);
        if (currentTeam != null) {
            if (currentTeam.getName().startsWith(TEAM_PREFIX)) {
                if (!currentTeam.getName().equals(teamName)) {
                    currentTeam.removeEntry(targetEntry);
                    if (currentTeam.getEntries().isEmpty()) {
                        currentTeam.unregister();
                    }
                    currentTeam = null;
                }
            } else {
                currentTeam.removeEntry(targetEntry);
                currentTeam = null;
            }
        }

        if (currentTeam == null) {
            currentTeam = board.getTeam(teamName);
            if (currentTeam == null) {
                currentTeam = board.registerNewTeam(teamName);
            }
            if (!currentTeam.hasEntry(targetEntry)) {
                currentTeam.addEntry(targetEntry);
            }
        }

        if (!Objects.equals(currentTeam.prefix(), nametag.prefix())) {
            currentTeam.prefix(nametag.prefix());
        }
        if (!Objects.equals(currentTeam.suffix(), nametag.suffix())) {
            currentTeam.suffix(nametag.suffix());
        }
        if (!Objects.equals(currentTeam.color(), nametag.color())) {
            currentTeam.color(nametag.color());
        }

        var expectedVisibility = nametag.visibility().toOptionStatus();
        if (currentTeam.getOption(Team.Option.NAME_TAG_VISIBILITY) != expectedVisibility) {
            currentTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, expectedVisibility);
        }

        var expectedCollision = nametag.collisionRule().toOptionStatus();
        if (currentTeam.getOption(Team.Option.COLLISION_RULE) != expectedCollision) {
            currentTeam.setOption(Team.Option.COLLISION_RULE, expectedCollision);
        }

        if (currentTeam.canSeeFriendlyInvisibles() != nametag.seeFriendlyInvisibles()) {
            currentTeam.setCanSeeFriendlyInvisibles(nametag.seeFriendlyInvisibles());
        }
        if (currentTeam.allowFriendlyFire() != nametag.friendlyFire()) {
            currentTeam.setAllowFriendlyFire(nametag.friendlyFire());
        }
    }

    /**
     * Removes the target from any Cotani nametag team on the viewer's scoreboard.
     *
     * @param viewer the viewer observing the scoreboard
     * @param targetEntry the entry/player name of the target
     */
    public static void removeTarget(Player viewer, String targetEntry) {
        Objects.requireNonNull(viewer, "Parameter 'viewer' must not be null");
        Objects.requireNonNull(targetEntry, "Parameter 'targetEntry' must not be null");

        if (!viewer.isOnline()) {
            return;
        }

        var board = viewer.getScoreboard();
        var currentTeam = board.getEntryTeam(targetEntry);
        if (currentTeam != null && currentTeam.getName().startsWith(TEAM_PREFIX)) {
            currentTeam.removeEntry(targetEntry);
            if (currentTeam.getEntries().isEmpty()) {
                currentTeam.unregister();
            }
        }
    }

    /**
     * Clears and unregisters all Cotani nametag teams on the viewer's scoreboard.
     *
     * @param viewer the viewer observing the scoreboard
     */
    public static void clearAllTeams(Player viewer) {
        Objects.requireNonNull(viewer, "Parameter 'viewer' must not be null");

        if (!viewer.isOnline()) {
            return;
        }

        var board = viewer.getScoreboard();
        var teams = new ArrayList<>(board.getTeams());
        for (var team : teams) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    private static String buildTeamName(int priority, String playerName) {
        var clamped = Math.clamp(priority, 0, MAX_PRIORITY);
        var d0 = (char) ('0' + (clamped / 1000) % 10);
        var d1 = (char) ('0' + (clamped / 100) % 10);
        var d2 = (char) ('0' + (clamped / 10) % 10);
        var d3 = (char) ('0' + clamped % 10);

        return TEAM_PREFIX + d0 + d1 + d2 + d3 + "_" + playerName;
    }
}
