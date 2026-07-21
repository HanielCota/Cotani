package com.cotani.teleport.safety;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.SafeLocationOptions;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DefaultSafeLocationResolverTest {

    private static final SafeLocationOptions OPTIONS = new SafeLocationOptions(2, 8, true, true, false);

    private final PaperTaskScheduler scheduler = mockScheduler();

    private DefaultSafeLocationResolver newResolver() {
        return new DefaultSafeLocationResolver(scheduler);
    }

    @SuppressWarnings("unchecked")
    private static PaperTaskScheduler mockScheduler() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        doAnswer(invocation -> CompletableFuture.completedFuture(Optional.empty()))
                .when(scheduler)
                .supply(any(ExecutionTarget.class), anyString(), any(Supplier.class));
        return scheduler;
    }

    private static World mockWorld(int chunkX, int chunkZ) {
        World world = Mockito.mock(World.class);
        UUID uid = UUID.randomUUID();
        when(world.getUID()).thenReturn(uid);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(chunkX, chunkZ)).thenReturn(true);
        when(world.getChunkAtAsync(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        return world;
    }

    @Test
    void nullWorldReturnsEmpty() {
        var resolver = newResolver();
        var target = new Location(null, 0, 64, 0);

        var result = resolver.resolve(target, OPTIONS).join();

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchIsScheduledOnTargetRegion() {
        var chunkX = 5;
        var chunkZ = -3;
        var world = mockWorld(chunkX, chunkZ);
        var resolver = newResolver();
        var target = new Location(world, chunkX * 16 + 3.0, 64, chunkZ * 16 + 7.0);

        resolver.resolve(target, OPTIONS).join();

        var captor = ArgumentCaptor.forClass(ExecutionTarget.class);
        verify(scheduler).supply(captor.capture(), anyString(), any(Supplier.class));
        var targetRegion = captor.getValue();
        assertTrue(targetRegion instanceof ExecutionTarget.Region region
                && region.chunkX() == chunkX
                && region.chunkZ() == chunkZ
                && region.worldId().equals(world.getUID()));
    }

    @Test
    void chunkIsLoadedBeforeSchedulingRegionTask() {
        var chunkX = 0;
        var chunkZ = 0;
        var world = mockWorld(chunkX, chunkZ);
        var resolver = newResolver();
        var target = new Location(world, 3.0, 64, 7.0);

        resolver.resolve(target, OPTIONS).join();

        verify(world).getChunkAtAsync(chunkX, chunkZ);
    }

    @Test
    @SuppressWarnings("unchecked")
    void regionTargetMatchesTargetChunkForCrossRegionSearch() {
        var targetChunkX = 5;
        var targetChunkZ = -3;
        var world = mockWorld(targetChunkX, targetChunkZ);
        var resolver = newResolver();
        var target = new Location(world, targetChunkX * 16 + 3.0, 64, targetChunkZ * 16 + 7.0);

        resolver.resolve(target, OPTIONS).join();

        var captor = ArgumentCaptor.forClass(ExecutionTarget.class);
        verify(scheduler).supply(captor.capture(), anyString(), any(Supplier.class));
        var targetRegion = captor.getValue();
        assertTrue(targetRegion instanceof ExecutionTarget.Region region
                && region.chunkX() == targetChunkX
                && region.chunkZ() == targetChunkZ
                && region.worldId().equals(world.getUID()));
    }
}
