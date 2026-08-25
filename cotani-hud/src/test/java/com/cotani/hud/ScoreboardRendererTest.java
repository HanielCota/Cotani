package com.cotani.hud;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.hud.internal.ScoreboardRenderer;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ScoreboardRendererTest {

    private MockedStatic<Bukkit> bukkitStatic;
    private PaperTaskScheduler scheduler;
    private Player player;
    private Server server;
    private ScoreboardManager scoreboardManager;
    private Scoreboard scoreboard;
    private Objective objective;
    private Team team;
    private Score score;
    private Criteria criteria;

    @BeforeEach
    void setUp() {
        bukkitStatic = mockStatic(Bukkit.class);
        server = mock(Server.class);
        bukkitStatic.when(Bukkit::getServer).thenReturn(server);

        scheduler = mock(PaperTaskScheduler.class);
        player = mock(Player.class);
        scoreboardManager = mock(ScoreboardManager.class);
        scoreboard = mock(Scoreboard.class);
        objective = mock(Objective.class);
        team = mock(Team.class);
        score = mock(Score.class);
        criteria = mock(Criteria.class);

        var playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(server.getScoreboardManager()).thenReturn(scoreboardManager);
        when(server.getScoreboardCriteria(anyString())).thenReturn(criteria);
        bukkitStatic.when(() -> Bukkit.getScoreboardCriteria(anyString())).thenReturn(criteria);

        when(scoreboardManager.getNewScoreboard()).thenReturn(scoreboard);
        when(scoreboard.registerNewObjective(anyString(), any(Criteria.class), any(Component.class)))
                .thenReturn(objective);
        when(scoreboard.registerNewTeam(anyString())).thenReturn(team);
        when(scoreboard.getTeam(anyString())).thenReturn(null).thenReturn(team);
        when(objective.getScore(anyString())).thenReturn(score);

        when(server.getPlayer(any(UUID.class))).thenReturn(player);
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .entity(any(Player.class), any(Runnable.class));

        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .entity(any(UUID.class), any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        if (bukkitStatic != null) {
            bukkitStatic.close();
        }
    }

    @Test
    void shouldInitializeScoreboardAndRenderLines() {
        var renderer = new ScoreboardRenderer(player, scheduler);
        var title = Component.text("Title");
        var line1 = Component.text("Line 1");

        renderer.renderTitle(title);
        renderer.renderLine(15, line1);

        verify(objective).setDisplaySlot(DisplaySlot.SIDEBAR);
        verify(player).setScoreboard(scoreboard);
        verify(team).prefix(line1);
        verify(score).setScore(15);

        renderer.removeLine(15);
        verify(team).unregister();

        renderer.close();
    }
}
