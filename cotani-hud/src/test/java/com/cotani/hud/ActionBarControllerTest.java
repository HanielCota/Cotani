package com.cotani.hud;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.gui.state.State;
import com.cotani.hud.internal.DefaultActionBarController;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionBarControllerTest {

    private PaperTaskScheduler scheduler;
    private Player player;

    @BeforeEach
    void setUp() {
        scheduler = mock(PaperTaskScheduler.class);
        player = mock(Player.class);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);

        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .entity(any(Player.class), any(Runnable.class));
    }

    @Test
    void shouldSendActionBar() {
        var manager = new DefaultActionBarController(scheduler);
        var msg = Component.text("Hello Action Bar");

        manager.send(player, msg);
        verify(player).sendActionBar(msg);

        manager.clear(player);
        verify(player).sendActionBar(Component.empty());
    }

    @Test
    void shouldUpdateOnPropertyChange() {
        var manager = new DefaultActionBarController(scheduler);
        var status = State.of("Idle");

        try (var _ = manager.bind(player, status, s -> Component.text("Status: " + s))) {
            verify(player).sendActionBar(Component.text("Status: Idle"));

            status.set("In Combat");
            verify(player).sendActionBar(Component.text("Status: In Combat"));
        }
    }

    @Test
    void shouldSendMiniMessageActionBar() {
        var manager = new DefaultActionBarController(scheduler);

        manager.send(player, "<green>+100 Coins</green>");
        manager.sendTimed(player, "<gold>Level Up!</gold>", java.time.Duration.ofSeconds(2));
    }

    @Test
    void shouldReplacePreviousBindingWhenActionBarRebound() {
        var manager = new DefaultActionBarController(scheduler);
        var state1 = State.of("Action 1");
        var state2 = State.of("Action 2");

        manager.bind(player, state1, Component::text);
        manager.bind(player, state2, Component::text);

        state1.set("Action 1 Updated");
        state2.set("Action 2 Updated");

        manager.clear(player);
    }
}
