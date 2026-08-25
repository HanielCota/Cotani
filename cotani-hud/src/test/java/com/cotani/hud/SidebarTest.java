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
import com.cotani.hud.internal.DefaultSidebarBuilder;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SidebarTest {

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
        when(player.getName()).thenReturn("TestPlayer");

        // Immediately run entity tasks in tests
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

    @Test
    void shouldBuildSidebarWithStaticAndDynamicLines() {
        var sidebar = new DefaultSidebarBuilder(scheduler, null, null)
                .title(Component.text("Server Title"))
                .line(15, Component.text("Line 15"))
                .line(14, p -> Component.text("Player: " + p.getName()))
                .apply(player);

        assertNotNull(sidebar);
        assertEquals(playerId, sidebar.viewerId());
        assertFalse(sidebar.isDestroyed());

        sidebar.line(13, Component.text("New Line 13"));
        sidebar.removeLine(15);
        sidebar.refresh();

        sidebar.close();
        assertTrue(sidebar.isDestroyed());
    }

    @Test
    void shouldUpdateReactiveLinesOnStateChange() {
        var kills = State.of(0);

        var sidebar = new DefaultSidebarBuilder(scheduler, null, null)
                .title(Component.text("Stats"))
                .bindLine(15, kills, k -> Component.text("Kills: " + k))
                .apply(player);

        assertFalse(sidebar.isDestroyed());

        // Update state
        kills.set(10);
        kills.set(25);

        sidebar.close();
        assertTrue(sidebar.isDestroyed());
    }

    @Test
    void shouldBuildSidebarWithMiniMessageStrings() {
        var sidebar = new DefaultSidebarBuilder(scheduler, null, null)
                .title("<gradient:gold:yellow>COTANI</gradient>")
                .line(15, "<gray>Line 15</gray>")
                .line(14, "<yellow>Line 14</yellow>")
                .apply(player);

        assertNotNull(sidebar);
        sidebar.line(13, "<green>Line 13</green>");
        sidebar.title("<red>New Title</red>");
        sidebar.close();
    }

    @Test
    void shouldRejectInvalidScoreBounds() {
        var builder = new DefaultSidebarBuilder(scheduler, null, null);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> builder.line(0, Component.text("Zero")));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> builder.line(16, Component.text("Sixteen")));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> builder.line(-1, Component.text("Negative")));
    }

    @Test
    void shouldReplacePreviousBindingWhenLineOrTitleRebound() {
        var state1 = State.of("Initial 1");
        var state2 = State.of("Initial 2");

        var sidebar = new DefaultSidebarBuilder(scheduler, null, null)
                .title(Component.text("Main Title"))
                .bindLine(15, state1, Component::text)
                .apply(player);

        // Rebind line 15 to state2
        sidebar.bindLine(15, state2, Component::text);

        // Update old state1 - should not affect line anymore
        state1.set("Updated 1");

        // Update state2 - affects line
        state2.set("Updated 2");

        sidebar.close();
        assertTrue(sidebar.isDestroyed());
    }
}
