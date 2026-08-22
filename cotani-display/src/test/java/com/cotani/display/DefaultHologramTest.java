package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.display.api.HologramLine;
import com.cotani.display.api.ItemLine;
import com.cotani.display.api.TextLine;
import com.cotani.display.impl.DefaultHologram;
import com.cotani.display.impl.DefaultHologramService;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultHologramTest {

    private PaperTaskScheduler scheduler;
    private DefaultHologramService service;
    private Executor directExecutor;

    @BeforeEach
    void setUp() {
        this.directExecutor = Runnable::run;
        this.scheduler = mock(PaperTaskScheduler.class);
        when(scheduler.regionExecutor(any(Location.class))).thenReturn(directExecutor);
        this.service = new DefaultHologramService(scheduler);
    }

    private World createMockWorld() {
        var world = mock(World.class);
        when(world.spawnEntity(any(Location.class), any(org.bukkit.entity.EntityType.class)))
                .thenAnswer(invocation -> {
                    var type = (org.bukkit.entity.EntityType) invocation.getArgument(1);
                    return switch (type) {
                        case TEXT_DISPLAY -> {
                            var td = mock(org.bukkit.entity.TextDisplay.class);
                            when(td.getUniqueId()).thenReturn(UUID.randomUUID());
                            yield td;
                        }
                        case ITEM_DISPLAY -> {
                            var id = mock(org.bukkit.entity.ItemDisplay.class);
                            when(id.getUniqueId()).thenReturn(UUID.randomUUID());
                            yield id;
                        }
                        case BLOCK_DISPLAY -> {
                            var bd = mock(org.bukkit.entity.BlockDisplay.class);
                            when(bd.getUniqueId()).thenReturn(UUID.randomUUID());
                            yield bd;
                        }
                        case INTERACTION -> {
                            var inter = mock(org.bukkit.entity.Interaction.class);
                            when(inter.getUniqueId()).thenReturn(UUID.randomUUID());
                            yield inter;
                        }
                        default -> {
                            var ent = mock(org.bukkit.entity.Entity.class);
                            when(ent.getUniqueId()).thenReturn(UUID.randomUUID());
                            yield ent;
                        }
                    };
                });
        return world;
    }

    @Test
    void shouldThrowWhenLocationNotSpawned() {
        var hologram = new DefaultHologram(
                UUID.randomUUID(),
                "test",
                List.of(TextLine.of(Component.text("Line"))),
                0.28,
                false,
                null,
                scheduler,
                service);

        assertThrows(IllegalStateException.class, hologram::location);
        assertFalse(hologram.isSpawned());
        assertEquals(1, hologram.lineCount());
    }

    @Test
    void shouldSpawnAndReturnLocation() {
        var world = createMockWorld();
        var location = new Location(world, 10.0, 64.0, 10.0);

        var hologram = new DefaultHologram(
                UUID.randomUUID(),
                "test",
                List.of(TextLine.of(Component.text("Line"))),
                0.28,
                false,
                null,
                scheduler,
                service);

        hologram.spawnAsync(location).toCompletableFuture().join();

        assertEquals(location, hologram.location());
    }

    @Test
    void shouldUpdateLinesAndModifyState() {
        var world = createMockWorld();
        var location = new Location(world, 10.0, 64.0, 10.0);

        var initialLine = TextLine.of(Component.text("Initial"));
        var hologram = new DefaultHologram(
                UUID.randomUUID(), "test", List.of(initialLine), 0.28, false, null, scheduler, service);

        hologram.spawnAsync(location).toCompletableFuture().join();

        var updatedLine = TextLine.of(Component.text("Updated"));
        hologram.updateLineAsync(0, updatedLine).toCompletableFuture().join();

        assertEquals(updatedLine, hologram.lines().get(0));

        // Add line
        var itemLine = ItemLine.of(mock(ItemStack.class));
        hologram.addLineAsync(itemLine).toCompletableFuture().join();
        assertEquals(2, hologram.lineCount());

        // Remove line
        hologram.removeLineAsync(0).toCompletableFuture().join();
        assertEquals(1, hologram.lineCount());
        assertEquals(itemLine, hologram.lines().get(0));
    }

    @Test
    void shouldTeleportAcrossSameAndDifferentRegions() {
        var world1 = createMockWorld();
        var world2 = createMockWorld();
        var loc1 = new Location(world1, 10.0, 64.0, 10.0);
        var loc2 = new Location(world1, 12.0, 64.0, 12.0); // same region
        var loc3 = new Location(world2, 500.0, 64.0, 500.0); // different region/world

        var hologram = new DefaultHologram(
                UUID.randomUUID(),
                "teleport_test",
                List.of(TextLine.of(Component.text("Line"))),
                0.28,
                false,
                null,
                scheduler,
                service);

        hologram.spawnAsync(loc1).toCompletableFuture().join();
        assertEquals(loc1, hologram.location());

        // Teleport same region
        hologram.teleportAsync(loc2).toCompletableFuture().join();
        assertEquals(loc2, hologram.location());

        // Teleport different region
        hologram.teleportAsync(loc3).toCompletableFuture().join();
        assertEquals(loc3, hologram.location());
    }

    @Test
    void shouldDestroyIdempotently() {
        var world = createMockWorld();
        var location = new Location(world, 10.0, 64.0, 10.0);

        var hologram = new DefaultHologram(
                UUID.randomUUID(),
                "destroy_test",
                List.of(TextLine.of(Component.text("Line"))),
                0.28,
                false,
                null,
                scheduler,
                service);

        hologram.spawnAsync(location).toCompletableFuture().join();
        hologram.destroyAsync().toCompletableFuture().join();

        assertFalse(hologram.isSpawned());

        // Subsequent destroy calls complete safely
        hologram.destroyAsync().toCompletableFuture().join();
        assertFalse(hologram.isSpawned());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldThrowOnNullHologramArguments() {
        assertThrows(
                NullPointerException.class,
                () -> new DefaultHologram(null, "test", List.of(), 0.28, false, null, scheduler, service));
        assertThrows(
                NullPointerException.class,
                () -> new DefaultHologram(UUID.randomUUID(), "test", null, 0.28, false, null, scheduler, service));
        assertThrows(
                NullPointerException.class,
                () -> new DefaultHologram(UUID.randomUUID(), "test", List.of(), 0.28, false, null, null, service));

        var hologram = new DefaultHologram(
                UUID.randomUUID(),
                "test",
                List.of(TextLine.of(Component.text("Line"))),
                0.28,
                false,
                null,
                scheduler,
                service);

        assertThrows(NullPointerException.class, () -> hologram.spawnAsync(null));
        assertThrows(NullPointerException.class, () -> hologram.teleportAsync(null));
        assertThrows(NullPointerException.class, () -> hologram.updateLineAsync(0, (HologramLine) null));
        assertThrows(NullPointerException.class, () -> hologram.updateLineAsync(0, (Component) null));
        assertThrows(NullPointerException.class, () -> hologram.updateLineAsync(0, (ItemStack) null));
        assertThrows(NullPointerException.class, () -> hologram.addLineAsync((HologramLine) null));
        assertThrows(NullPointerException.class, () -> hologram.addLineAsync((Component) null));
        assertThrows(NullPointerException.class, () -> hologram.addLineAsync((ItemStack) null));
    }
}
