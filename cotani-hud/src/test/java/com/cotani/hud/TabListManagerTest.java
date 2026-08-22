package com.cotani.hud;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.gui.state.State;
import com.cotani.hud.impl.DefaultTabListManager;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TabListManagerTest {

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
    void shouldSetHeaderAndFooter() {
        var manager = new DefaultTabListManager(scheduler);
        var header = Component.text("Welcome");
        var footer = Component.text("Store: shop.example.com");

        manager.setHeader(player, header);
        verify(player).sendPlayerListHeaderAndFooter(header, Component.empty());

        manager.setFooter(player, footer);
        verify(player).sendPlayerListHeaderAndFooter(header, footer);

        manager.clear(player);
        verify(player).sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    @Test
    void shouldUpdateOnReactiveBinding() {
        var manager = new DefaultTabListManager(scheduler);
        var online = State.of(1);

        try (var _ = manager.bindHeader(player, online, count -> Component.text("Online: " + count))) {
            verify(player).sendPlayerListHeaderAndFooter(Component.text("Online: 1"), Component.empty());

            online.set(5);
            verify(player).sendPlayerListHeaderAndFooter(Component.text("Online: 5"), Component.empty());
        }
    }

    @Test
    void shouldSetHeaderAndFooterWithMiniMessage() {
        var manager = new DefaultTabListManager(scheduler);

        manager.setHeader(player, "<gradient:aqua:blue>Header</gradient>");
        manager.setFooter(player, "<yellow>Footer</yellow>");
        manager.setHeaderAndFooter(player, "<aqua>H2</aqua>", "<gold>F2</gold>");
    }

    @Test
    void shouldReplacePreviousBindingWhenHeaderRebound() {
        var manager = new DefaultTabListManager(scheduler);
        var state1 = State.of("H1");
        var state2 = State.of("H2");

        manager.bindHeader(player, state1, Component::text);
        manager.bindHeader(player, state2, Component::text);

        state1.set("H1-Updated");
        state2.set("H2-Updated");

        manager.clear(player);
    }
}
