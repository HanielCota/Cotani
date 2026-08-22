package com.cotani.command.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.command.api.CommandBuilder;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class BukkitCommandWrapperTest {

    @Test
    void shouldInitializePropertiesFromNode() {
        var node = CommandBuilder.of("spawn")
                .aliases("sp", "hub")
                .description("Teleport to spawn")
                .usage("/spawn")
                .permission("cotani.spawn")
                .executes(ctx -> {})
                .build();

        var dispatcher = mock(DefaultCommandDispatcher.class);
        var wrapper = new BukkitCommandWrapper(node, dispatcher);

        assertEquals("spawn", wrapper.getName());
        assertEquals("Teleport to spawn", wrapper.getDescription());
        assertEquals("/spawn", wrapper.getUsage());
        assertEquals("cotani.spawn", wrapper.getPermission());
        assertTrue(wrapper.getAliases().containsAll(List.of("sp", "hub")));
        assertSame(node, wrapper.node());
    }

    @Test
    void shouldDelegateExecuteAndTabCompleteToDispatcher() {
        var node = CommandBuilder.of("ping").executes(ctx -> {}).build();
        var dispatcher = mock(DefaultCommandDispatcher.class);
        var wrapper = new BukkitCommandWrapper(node, dispatcher);
        var sender = mock(CommandSender.class);

        when(dispatcher.complete(node, sender, "ping", List.of("arg1"))).thenReturn(List.of("suggestion1"));

        var executed = wrapper.execute(sender, "ping", new String[] {"arg1"});
        assertTrue(executed);
        verify(dispatcher).dispatch(node, sender, "ping", List.of("arg1"));

        var completions = wrapper.tabComplete(sender, "ping", new String[] {"arg1"});
        assertEquals(List.of("suggestion1"), completions);

        var locCompletions = wrapper.tabComplete(sender, "ping", new String[] {"arg1"}, mock(Location.class));
        assertEquals(List.of("suggestion1"), locCompletions);
    }
}
