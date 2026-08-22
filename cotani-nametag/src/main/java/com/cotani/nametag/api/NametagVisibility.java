package com.cotani.nametag.api;

import java.util.Objects;
import org.bukkit.scoreboard.Team;

/**
 * Defines visibility policies for player nametags above their heads.
 */
public enum NametagVisibility {
    /**
     * Nametag is always visible to all players.
     */
    ALWAYS(Team.OptionStatus.ALWAYS),

    /**
     * Nametag is never visible to any player.
     */
    NEVER(Team.OptionStatus.NEVER),

    /**
     * Nametag is hidden for players on other teams.
     */
    HIDE_FOR_OTHER_TEAMS(Team.OptionStatus.FOR_OTHER_TEAMS),

    /**
     * Nametag is hidden for players on the same team.
     */
    HIDE_FOR_OWN_TEAM(Team.OptionStatus.FOR_OWN_TEAM);

    private final Team.OptionStatus optionStatus;

    NametagVisibility(Team.OptionStatus optionStatus) {
        this.optionStatus = Objects.requireNonNull(optionStatus, "optionStatus");
    }

    /**
     * Converts this visibility rule to Bukkit's {@link Team.OptionStatus}.
     *
     * @return the corresponding OptionStatus
     */
    public Team.OptionStatus toOptionStatus() {
        return optionStatus;
    }
}
