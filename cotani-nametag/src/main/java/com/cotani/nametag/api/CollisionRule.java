package com.cotani.nametag.api;

import java.util.Objects;
import org.bukkit.scoreboard.Team;

/**
 * Defines physical entity collision rules associated with player scoreboard teams.
 */
public enum CollisionRule {
    /**
     * Physical collisions are always enabled for all players.
     */
    ALWAYS(Team.OptionStatus.ALWAYS),

    /**
     * Physical collisions are completely disabled for this team.
     */
    NEVER(Team.OptionStatus.NEVER),

    /**
     * Players on this team only push players on other teams.
     */
    PUSH_OTHER_TEAMS(Team.OptionStatus.FOR_OTHER_TEAMS),

    /**
     * Players on this team only push teammates.
     */
    PUSH_OWN_TEAM(Team.OptionStatus.FOR_OWN_TEAM);

    private final Team.OptionStatus optionStatus;

    CollisionRule(Team.OptionStatus optionStatus) {
        this.optionStatus = Objects.requireNonNull(optionStatus, "optionStatus");
    }

    /**
     * Converts this collision rule to Bukkit's {@link Team.OptionStatus}.
     *
     * @return the corresponding OptionStatus
     */
    public Team.OptionStatus toOptionStatus() {
        return optionStatus;
    }
}
