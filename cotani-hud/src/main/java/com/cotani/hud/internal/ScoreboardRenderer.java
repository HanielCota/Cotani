package com.cotani.hud.internal;

import com.cotani.api.InternalApi;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * Low-level, zero-flicker scoreboard renderer using Paper team prefixes.
 */
@InternalApi
public final class ScoreboardRenderer {

    private static final String OBJECTIVE_NAME = "cotani_sb";
    private static final String[] COLOR_CODES = new String[] {
        "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r", "§7§r", "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r",
        "§e§r", "§f§r"
    };

    private final UUID playerId;
    private final PaperTaskScheduler scheduler;
    private final AtomicReference<Component> renderedTitle = new AtomicReference<>(Component.empty());

    private @Nullable Scoreboard scoreboard;
    private @Nullable Objective objective;
    private boolean initialized;
    private boolean closed;

    public ScoreboardRenderer(UUID playerId, PaperTaskScheduler scheduler) {
        this.playerId = Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
    }

    public ScoreboardRenderer(Player player, PaperTaskScheduler scheduler) {
        this(
                Objects.requireNonNull(player, "Parameter 'player' must not be null")
                        .getUniqueId(),
                scheduler);
    }

    private void ensureInitialized(Player player) {
        if (initialized || closed) {
            return;
        }

        var server = Bukkit.getServer();
        if (server == null) {
            return;
        }

        var manager = server.getScoreboardManager();
        if (manager == null) {
            return;
        }

        var board = manager.getNewScoreboard();
        var criteria = server.getScoreboardCriteria("dummy");
        if (criteria == null) {
            criteria = Criteria.DUMMY;
        }

        if (criteria != null) {
            this.objective = board.registerNewObjective(
                    OBJECTIVE_NAME, criteria, Objects.requireNonNullElse(renderedTitle.get(), Component.empty()));
        }

        if (this.objective != null) {
            this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            this.scoreboard = board;
            player.setScoreboard(board);
            this.initialized = true;
        }
    }

    /**
     * Updates the objective title.
     *
     * @param title the new title component
     */
    public void renderTitle(Component title) {
        Objects.requireNonNull(title, "Parameter 'title' must not be null");
        if (closed) {
            return;
        }

        this.renderedTitle.set(title);
        scheduler.entity(playerId, () -> {
            if (closed) {
                return;
            }
            var server = Bukkit.getServer();
            if (server == null) {
                return;
            }
            var player = server.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            ensureInitialized(player);
            if (objective != null) {
                objective.displayName(title);
            }
        });
    }

    /**
     * Updates or sets a single line.
     *
     * @param score line score index (1 to 15)
     * @param content line content
     */
    public void renderLine(int score, Component content) {
        Objects.requireNonNull(content, "Parameter 'content' must not be null");
        if (score < 1 || score > 15 || closed) {
            return;
        }

        scheduler.entity(playerId, () -> {
            if (closed) {
                return;
            }
            var server = Bukkit.getServer();
            if (server == null) {
                return;
            }
            var player = server.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            ensureInitialized(player);
            if (scoreboard == null || objective == null) {
                return;
            }

            var entryKey = getEntryKey(score);
            var teamName = "c_sb_" + score;
            var team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.addEntry(entryKey);
            }

            team.prefix(content);
            objective.getScore(entryKey).setScore(score);
        });
    }

    /**
     * Removes a line from the scoreboard.
     *
     * @param score score index
     */
    public void removeLine(int score) {
        if (score < 1 || score > 15 || closed) {
            return;
        }

        scheduler.entity(playerId, () -> {
            if (closed || scoreboard == null) {
                return;
            }

            var entryKey = getEntryKey(score);
            scoreboard.resetScores(entryKey);

            var teamName = "c_sb_" + score;
            var team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        });
    }

    /**
     * Destroys and detaches the scoreboard.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        scheduler.entity(playerId, () -> {
            var server = Bukkit.getServer();
            if (server != null) {
                var player = server.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    var manager = server.getScoreboardManager();
                    if (manager != null) {
                        player.setScoreboard(manager.getMainScoreboard());
                    }
                }
            }
        });
    }

    private static String getEntryKey(int score) {
        return COLOR_CODES[score % COLOR_CODES.length];
    }
}
