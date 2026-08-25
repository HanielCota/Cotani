package com.cotani.task.impl.scheduler;

import com.cotani.api.InternalApi;
import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.impl.executor.VirtualThreadExecutor;
import com.cotani.task.impl.task.FutureSchedulerTask;
import com.cotani.task.impl.task.LazySchedulerTask;
import com.cotani.task.impl.task.PaperSchedulerTask;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

@InternalApi
public final class PaperPlatformScheduler implements PlatformScheduler, AutoCloseable {
    private final Plugin plugin;
    private final VirtualThreadExecutor virtualThreadExecutor;
    private final Set<SchedulerTask> ownedTasks = ConcurrentHashMap.newKeySet();
    private final Set<SchedulerTask> ownedLookupTasks = ConcurrentHashMap.newKeySet();

    private PaperPlatformScheduler(Plugin plugin, VirtualThreadExecutor virtualThreadExecutor) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.virtualThreadExecutor = Objects.requireNonNull(virtualThreadExecutor, "virtualThreadExecutor");
    }

    public static PaperPlatformScheduler create(Plugin plugin, VirtualThreadExecutor virtualThreadExecutor) {
        return new PaperPlatformScheduler(plugin, virtualThreadExecutor);
    }

    @Override
    public SchedulerTask runAsync(TaskMetadata metadata, Runnable runnable) {
        return track(new FutureSchedulerTask(virtualThreadExecutor.submit(metadata, runnable)));
    }

    @Override
    public SchedulerTask runAsyncLater(TaskMetadata metadata, Runnable runnable, Duration delay) {
        return track(new FutureSchedulerTask(virtualThreadExecutor.schedule(metadata, runnable, delay.toMillis())));
    }

    @Override
    public SchedulerTask runAsyncTimer(
            TaskMetadata metadata, Runnable runnable, Duration initialDelay, Duration period) {
        return track(new FutureSchedulerTask(virtualThreadExecutor.scheduleAtFixedRate(
                metadata, runnable, initialDelay.toMillis(), period.toMillis())));
    }

    @Override
    public SchedulerTask runGlobal(TaskMetadata metadata, Runnable runnable) {
        var task = Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> runnable.run());

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runGlobalLater(TaskMetadata metadata, Runnable runnable, Duration delay) {
        var task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> runnable.run(), Ticks.from(delay));

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runGlobalTimer(
            TaskMetadata metadata, Runnable runnable, Duration initialDelay, Duration period) {
        var task = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, ignored -> runnable.run(), Ticks.from(initialDelay), Ticks.from(period));

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runRegion(TaskMetadata metadata, Location location, Runnable runnable) {
        var task = Bukkit.getRegionScheduler().run(plugin, location, ignored -> runnable.run());

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runRegionLater(TaskMetadata metadata, Location location, Runnable runnable, Duration delay) {
        var task =
                Bukkit.getRegionScheduler().runDelayed(plugin, location, ignored -> runnable.run(), Ticks.from(delay));

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runRegionTimer(
            TaskMetadata metadata, Location location, Runnable runnable, Duration initialDelay, Duration period) {
        var task = Bukkit.getRegionScheduler()
                .runAtFixedRate(
                        plugin, location, ignored -> runnable.run(), Ticks.from(initialDelay), Ticks.from(period));

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runEntity(TaskMetadata metadata, Entity entity, Runnable runnable, Runnable retired) {
        var task = entity.getScheduler().run(plugin, ignored -> runnable.run(), retired);

        if (task == null) {
            return track(SchedulerTask.noop());
        }

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runEntityLater(
            TaskMetadata metadata, Entity entity, Runnable runnable, Runnable retired, Duration delay) {
        var task = entity.getScheduler().runDelayed(plugin, ignored -> runnable.run(), retired, Ticks.from(delay));

        if (task == null) {
            return track(SchedulerTask.noop());
        }

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runEntityTimer(
            TaskMetadata metadata,
            Entity entity,
            Runnable runnable,
            Runnable retired,
            Duration initialDelay,
            Duration period) {
        var task = entity.getScheduler()
                .runAtFixedRate(
                        plugin, ignored -> runnable.run(), retired, Ticks.from(initialDelay), Ticks.from(period));

        if (task == null) {
            return track(SchedulerTask.noop());
        }

        return track(new PaperSchedulerTask(task));
    }

    @Override
    public SchedulerTask runRegion(TaskMetadata metadata, UUID worldId, int chunkX, int chunkZ, Runnable runnable) {
        return scheduleAfterWorldLookup(
                metadata,
                worldId,
                (world) -> runRegion(metadata, new Location(world, chunkX << 4, 0, chunkZ << 4), runnable));
    }

    @Override
    public SchedulerTask runRegionLater(
            TaskMetadata metadata, UUID worldId, int chunkX, int chunkZ, Runnable runnable, Duration delay) {
        return scheduleAfterWorldLookup(
                metadata,
                worldId,
                world -> runRegionLater(metadata, new Location(world, chunkX << 4, 0, chunkZ << 4), runnable, delay));
    }

    @Override
    public SchedulerTask runRegionTimer(
            TaskMetadata metadata,
            UUID worldId,
            int chunkX,
            int chunkZ,
            Runnable runnable,
            Duration initialDelay,
            Duration period) {
        return scheduleAfterWorldLookup(
                metadata,
                worldId,
                world -> runRegionTimer(
                        metadata, new Location(world, chunkX << 4, 0, chunkZ << 4), runnable, initialDelay, period));
    }

    @Override
    public SchedulerTask runEntity(TaskMetadata metadata, UUID entityId, Runnable runnable, Runnable retired) {
        return scheduleAfterEntityLookup(
                metadata, entityId, retired, entity -> runEntity(metadata, entity, runnable, retired));
    }

    @Override
    public SchedulerTask runEntityLater(
            TaskMetadata metadata, UUID entityId, Runnable runnable, Runnable retired, Duration delay) {
        return scheduleAfterEntityLookup(
                metadata, entityId, retired, entity -> runEntityLater(metadata, entity, runnable, retired, delay));
    }

    @Override
    public SchedulerTask runEntityTimer(
            TaskMetadata metadata,
            UUID entityId,
            Runnable runnable,
            Runnable retired,
            Duration initialDelay,
            Duration period) {
        return scheduleAfterEntityLookup(
                metadata,
                entityId,
                retired,
                entity -> runEntityTimer(metadata, entity, runnable, retired, initialDelay, period));
    }

    @Override
    public void cancelOwnedTasks() {
        ownedTasks.forEach(SchedulerTask::cancel);
        ownedTasks.clear();
        ownedLookupTasks.forEach(SchedulerTask::cancel);
        ownedLookupTasks.clear();
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }

    private SchedulerTask track(SchedulerTask task) {
        ownedTasks.add(Objects.requireNonNull(task, "task"));
        return task;
    }

    private SchedulerTask scheduleAfterWorldLookup(
            TaskMetadata metadata, UUID worldId, Function<World, SchedulerTask> factory) {
        var lazy = new LazySchedulerTask();
        track(lazy);
        ownedLookupTasks.add(lazy);
        lazy.setupResult().whenComplete((_, _) -> ownedLookupTasks.remove(lazy));
        var setup = runGlobal(metadata, () -> {
            var world = Bukkit.getWorld(worldId);
            if (world == null) {
                lazy.failSetup(new IllegalStateException("World not found: " + worldId));
                return;
            }
            var task = factory.apply(world);
            lazy.setDelegate(task);
            lazy.completeSetup(task);
        });
        lazy.setSetupTask(setup);
        return lazy;
    }

    private SchedulerTask scheduleAfterEntityLookup(
            TaskMetadata metadata, UUID entityId, Runnable retired, Function<Entity, SchedulerTask> factory) {
        var lazy = new LazySchedulerTask();
        track(lazy);
        ownedLookupTasks.add(lazy);
        lazy.setupResult().whenComplete((_, _) -> ownedLookupTasks.remove(lazy));
        var setup = runGlobal(metadata, () -> {
            var entity = Bukkit.getEntity(entityId);
            if (entity == null) {
                retired.run();
                lazy.failSetup(new IllegalStateException("Entity not found: " + entityId));
                return;
            }
            var task = factory.apply(entity);
            lazy.setDelegate(task);
            lazy.completeSetup(task);
        });
        lazy.setSetupTask(setup);
        return lazy;
    }

    @Override
    public void close() {
        cancelOwnedTasks();
        virtualThreadExecutor.close();
    }

    public CompletionStage<Void> closeAsync() {
        cancelOwnedTasks();
        return virtualThreadExecutor.closeAsync();
    }
}
