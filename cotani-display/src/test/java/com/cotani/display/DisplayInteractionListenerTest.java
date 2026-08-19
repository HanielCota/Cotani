package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.display.api.Hologram;
import com.cotani.display.api.HologramClickHandler;
import com.cotani.display.api.HologramClickType;
import com.cotani.display.impl.DefaultHologramService;
import com.cotani.display.impl.DisplayInteractionListener;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

class DisplayInteractionListenerTest {

    @Test
    void shouldDispatchRightClickToHologramHandler() {
        var scheduler = mock(PaperTaskScheduler.class);
        var service = new DefaultHologramService(scheduler);
        var listener = new DisplayInteractionListener(service);

        var entityId = UUID.randomUUID();
        var hologram = mock(Hologram.class);
        when(hologram.id()).thenReturn(UUID.randomUUID());
        when(hologram.name()).thenReturn(Optional.empty());
        when(hologram.entityIds()).thenReturn(java.util.List.of(entityId));

        AtomicReference<HologramClickType> receivedClick = new AtomicReference<>();
        HologramClickHandler handler = (player, h, clickType) -> receivedClick.set(clickType);
        when(hologram.clickHandler()).thenReturn(Optional.of(handler));

        service.register(hologram);

        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isSneaking()).thenReturn(false);

        var interactionEntity = mock(Interaction.class);
        when(interactionEntity.getUniqueId()).thenReturn(entityId);

        var event = mock(PlayerInteractEntityEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getRightClicked()).thenReturn(interactionEntity);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerInteract(event);

        assertEquals(HologramClickType.RIGHT_CLICK, receivedClick.get());
    }

    @Test
    void shouldDispatchLeftClickDamageToHologramHandler() {
        var scheduler = mock(PaperTaskScheduler.class);
        var service = new DefaultHologramService(scheduler);
        var listener = new DisplayInteractionListener(service);

        var entityId = UUID.randomUUID();
        var hologram = mock(Hologram.class);
        when(hologram.id()).thenReturn(UUID.randomUUID());
        when(hologram.name()).thenReturn(Optional.empty());
        when(hologram.entityIds()).thenReturn(java.util.List.of(entityId));

        AtomicReference<HologramClickType> receivedClick = new AtomicReference<>();
        HologramClickHandler handler = (player, h, clickType) -> receivedClick.set(clickType);
        when(hologram.clickHandler()).thenReturn(Optional.of(handler));

        service.register(hologram);

        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isSneaking()).thenReturn(true);

        var interactionEntity = mock(Interaction.class);
        when(interactionEntity.getUniqueId()).thenReturn(entityId);

        var damageEvent = mock(EntityDamageByEntityEvent.class);
        when(damageEvent.getEntity()).thenReturn(interactionEntity);
        when(damageEvent.getDamager()).thenReturn(player);

        listener.onEntityDamage(damageEvent);

        assertEquals(HologramClickType.SHIFT_LEFT_CLICK, receivedClick.get());
    }
}
