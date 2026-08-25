package com.cotani.placeholder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.placeholder.api.PlaceholderContext;
import com.cotani.placeholder.internal.DefaultPlaceholderService;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaceholderServiceTest {

    private Plugin plugin;
    private PaperTaskScheduler scheduler;
    private DefaultPlaceholderService service;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        var server = mock(Server.class);
        var pm = mock(PluginManager.class);
        var meta = mock(io.papermc.paper.plugin.configuration.PluginMeta.class);

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getPluginMeta()).thenReturn(meta);
        when(meta.getVersion()).thenReturn("1.0.0");
        when(server.getPluginManager()).thenReturn(pm);

        scheduler = mock(PaperTaskScheduler.class);
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .entity(any(UUID.class), any(Runnable.class));
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .global(any(String.class), any(Runnable.class));
        service = new DefaultPlaceholderService(plugin, scheduler);
    }

    @Test
    void testRegisterSyncHandlerAndParse() {
        service.register("coins", (ctx, params) -> {
            if (params.equals("amount")) return "500";
            return "0";
        });

        String result = service.parse("You have {coins_amount} coins!");
        assertEquals("You have 500 coins!", result);

        String resultPercent = service.parse("You have %coins_amount% coins!");
        assertEquals("You have 500 coins!", resultPercent);
    }

    @Test
    void testRegisterAsyncHandlerAndParseAsync() {
        service.registerAsync("rank", (ctx, params) -> CompletableFuture.completedFuture("VIP"));

        CompletableFuture<String> future =
                service.parseAsync("Player rank: {rank_title}").toCompletableFuture();

        assertEquals("Player rank: VIP", future.join());
    }

    @Test
    void testRegisterRelationalHandler() {
        Player viewer = mock(Player.class);
        Player target = mock(Player.class);
        UUID viewerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(target.getUniqueId()).thenReturn(targetId);

        service.registerRelational("friend", (v, t, params) -> {
            if (Objects.equals(v, viewer) && Objects.equals(t, target)) {
                return "YES";
            }
            return "NO";
        });

        String parsed = service.parseRelational(viewer, target, "Are friends? %rel_friend_status%");
        assertEquals("Are friends? YES", parsed);
    }

    @Test
    void testParseComponentMiniMessage() {
        service.register("tag", (ctx, params) -> "<b>bold</b>");

        Component component = service.parseComponent("<green>Status: {tag}");
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        assertTrue(plain.contains("Status: bold"));
    }

    @Test
    void testPlaceholderContextParameters() {
        service.register(
                "custom",
                (ctx, params) ->
                        ctx.parameter("multiplier").map(Object::toString).orElse("1"));

        var ctx = PlaceholderContext.empty().with("multiplier", "5");
        String result = service.parse(ctx, "Multiplier: {custom}");

        assertEquals("Multiplier: 5", result);
    }

    @Test
    void testMultiSegmentIdentifierPrefix() {
        service.register("player_points", (ctx, params) -> {
            if (params.equals("balance")) return "999";
            return "0";
        });

        String result = service.parse("{player_points_balance}");
        assertEquals("999", result);
    }

    @Test
    void testAsyncHandlerFailureIsPropagated() {
        service.registerAsync("failing", (ctx, params) -> CompletableFuture.failedFuture(new RuntimeException("Boom")));

        var failure = assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> service.parseAsync("Error: {failing}")
                        .toCompletableFuture()
                        .join());

        var cause = assertInstanceOf(RuntimeException.class, failure.getCause());
        assertEquals("Boom", cause.getMessage());
    }

    @Test
    void testContextRejectsLiveBukkitObjectsAsParameters() {
        Player player = mock(Player.class);

        assertThrows(
                IllegalArgumentException.class, () -> PlaceholderContext.empty().with("player", player));
    }

    @Test
    void testTagResolverMiniMessage() {
        service.register("player", (ctx, params) -> {
            if (params.equals("name")) return "Haniel";
            return null;
        });

        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver resolver =
                service.tagResolver(PlaceholderContext.empty());

        Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<green>Hello <player_name>!</green>", resolver);

        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertEquals("Hello Haniel!", plain);
    }

    @Test
    void testUnregisterExpansion() {
        service.register("temp", (ctx, params) -> "active");
        assertEquals("active", service.parse("{temp}"));

        assertTrue(service.unregister("temp"));
        assertEquals("{temp}", service.parse("{temp}"));
    }
}
