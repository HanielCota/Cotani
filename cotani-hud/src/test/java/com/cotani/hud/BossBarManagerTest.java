package com.cotani.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.gui.state.State;
import com.cotani.hud.internal.DefaultBossBarManager;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BossBarManagerTest {

    private PaperTaskScheduler scheduler;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        scheduler = mock(PaperTaskScheduler.class);
        player = mock(Player.class);
        playerId = UUID.randomUUID();

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        when(scheduler.entity(any(UUID.class), any(Runnable.class))).thenReturn(null);
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .global(any(String.class), any(Runnable.class));
    }

    @Test
    void shouldBuildAndShowBossBar() {
        var manager = new DefaultBossBarManager(scheduler);

        var bar = manager.builder()
                .title(Component.text("Boss Fight"))
                .color(BossBar.Color.RED)
                .overlay(BossBar.Overlay.PROGRESS)
                .progress(0.75f)
                .show(player);

        assertNotNull(bar);
        assertTrue(bar.viewerIds().contains(playerId));
        assertEquals(0.75f, bar.adventureBar().progress());
        assertEquals(BossBar.Color.RED, bar.adventureBar().color());
        bar.progress(0.5f);
        assertEquals(0.5f, bar.adventureBar().progress());

        manager.clear(player);
        assertFalse(bar.viewerIds().contains(playerId));

        bar.close();
        assertTrue(bar.isDestroyed());
    }

    @Test
    void shouldBindReactiveStateToBossBar() {
        var manager = new DefaultBossBarManager(scheduler);
        var hp = State.of(1.0f);

        var bar = manager.builder().title(Component.text("Dragon")).build();

        bar.bindProgress(hp);
        bar.show(player);

        hp.set(0.2f);
        assertEquals(0.2f, bar.adventureBar().progress(), 0.001f);

        bar.close();
    }

    @Test
    void shouldBuildWithMiniMessageTitleAndExposeViewerIds() {
        var manager = new DefaultBossBarManager(scheduler);

        var bar = manager.builder().title("<red>Dragon Raid</red>").show(player);

        assertTrue(bar.viewerIds().contains(playerId));
        bar.title("<gold>Phase 2</gold>");

        bar.close();
    }

    @Test
    void shouldRemoveClosedBossBarFromActiveBarsInManager() {
        var manager = new DefaultBossBarManager(scheduler);

        var bar = manager.builder().title(Component.text("Raid")).show(player);

        assertEquals(1, manager.getBars(player).size());

        bar.close();

        assertEquals(0, manager.getBars(player).size());
    }
}
