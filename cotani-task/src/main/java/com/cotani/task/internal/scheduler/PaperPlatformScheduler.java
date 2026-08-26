package com.cotani.task.internal.scheduler;

import com.cotani.api.InternalApi;
import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.internal.executor.VirtualThreadExecutor;
import com.cotani.task.internal.task.FutureSchedulerTask;
import com.cotani.task.internal.task.LazySchedulerTask;
import com.cotani.task.internal.task.PaperSchedulerTask;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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
        return trackFuture((task, onTerminal) -> virtualThreadExecutor.submit(metadata, task, onTerminal), runnable);
    }

    @Override
    public SchedulerTask runAsyncLater(TaskMetadata metadata, Runnable runnable, Duration delay) {
        return trackFuture(
                (task, onTerminal) -> virtualThreadExecutor.schedule(metadata, task, delay.toMillis(), onTerminal),
                runnable);
    }

    @Override
    public SchedulerTask runAsyncTimer(
            TaskMetadata metadata, Runnable runnable, Duration initialDelay, Duration period) {
        return trackFuture(
                (task, onTerminal) -> virtualThreadExecutor.scheduleAtFixedRate(
                        metadata, task, initialDelay.toMillis(), period.toMillis(), onTerminal),
                runnable);
    }

    @Override
    public SchedulerTask runGlobal(TaskMetadata metadata, Runnable runnable) {
        return trackPaper(
                (scheduled, _) -> Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> scheduled.run()),
                runnable,
                true);
    }

    @Override
    public SchedulerTask runGlobalLater(TaskMetadata metadata, Runnable runnable, Duration delay) {
        return trackPaper(
                (scheduled, _) -> Bukkit.getGlobalRegionScheduler()
                        .runDelayed(plugin, ignored -> scheduled.run(), Ticks.from(delay)),
                runnable,
                true);
    }

    @Override
    public SchedulerTask runGlobalTimer(
            TaskMetadata metadata, Runnable runnable, Duration initialDelay, Duration period) {
        return trackPaper(
                (scheduled, _) -> Bukkit.getGlobalRegionScheduler()
                        .runAtFixedRate(
                                plugin, ignored -> scheduled.run(), Ticks.from(initialDelay), Ticks.from(period)),
                runnable,
                false);
    }

    @Override
    public SchedulerTask runRegion(TaskMetadata metadata, Location location, Runnable runnable) {
        return trackPaper(
                (scheduled, _) -> Bukkit.getRegionScheduler().run(plugin, location, ignored -> scheduled.run()),
                runnable,
                true);
    }

    @Override
    public SchedulerTask runRegionLater(TaskMetadata metadata, Location location, Runnable runnable, Duration delay) {
        return trackPaper(
                (scheduled, _) -> Bukkit.getRegionScheduler()
                        .runDelayed(plugin, location, ignored -> scheduled.run(), Ticks.from(delay)),
                runnable,
                true);
    }

    @Override
    public SchedulerTask runRegionTimer(
            TaskMetadata metadata, Location location, Runnable runnable, Duration initialDelay, Duration period) {
        return trackPaper(
                (scheduled, _) -> Bukkit.getRegionScheduler()
                        .runAtFixedRate(
                                plugin,
                                location,
                                ignored -> scheduled.run(),
                                Ticks.from(initialDelay),
                                Ticks.from(period)),
                runnable,
                false);
    }

    @Override
    public SchedulerTask runEntity(TaskMetadata metadata, Entity entity, Runnable runnable, Runnable retired) {
        return trackEntity(
                (scheduled, terminal) -> entity.getScheduler().run(plugin, ignored -> scheduled.run(), () -> {
                    try {
                        retired.run();
                    } finally {
                        terminal.run();
                    }
                }),
                runnable);
    }

    @Override
    public SchedulerTask runEntityLater(
            TaskMetadata metadata, Entity entity, Runnable runnable, Runnable retired, Duration delay) {
        return trackEntity(
                (scheduled, terminal) -> entity.getScheduler()
                        .runDelayed(
                                plugin,
                                ignored -> scheduled.run(),
                                () -> {
                                    try {
                                        retired.run();
                                    } finally {
                                        terminal.run();
                                    }
                                },
                                Ticks.from(delay)),
                runnable);
    }

    @Override
    public SchedulerTask runEntityTimer(
            TaskMetadata metadata,
            Entity entity,
            Runnable runnable,
            Runnable retired,
            Duration initialDelay,
            Duration period) {
        return trackEntity(
                (scheduled, terminal) -> entity.getScheduler()
                        .runAtFixedRate(
                                plugin,
                                ignored -> scheduled.run(),
                                () -> {
                                    try {
                                        retired.run();
                                    } finally {
                                        terminal.run();
                                    }
                                },
                                Ticks.from(initialDelay),
                                Ticks.from(period)),
                runnable);
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

    private SchedulerTask trackFuture(BiFunction<Runnable, Runnable, Future<Void>> scheduler, Runnable runnable) {
        var reference = new AtomicReference<SchedulerTask>();
        var terminal = new AtomicBoolean();
        Runnable onTerminal = () -> {
            terminal.set(true);
            var task = reference.get();
            if (task != null) {
                ownedTasks.remove(task);
            }
        };
        var future = scheduler.apply(runnable, onTerminal);
        var task = new FutureSchedulerTask(future, onTerminal);
        ownedTasks.add(task);
        reference.set(task);
        if (terminal.get()) {
            ownedTasks.remove(task);
        }
        return task;
    }

    private SchedulerTask trackPaper(
            BiFunction<Runnable, Runnable, io.papermc.paper.threadedregions.scheduler.ScheduledTask> scheduler,
            Runnable runnable,
            boolean removeAfterRun) {
        var reference = new AtomicReference<SchedulerTask>();
        var terminal = new AtomicBoolean();
        Runnable onTerminal = () -> {
            terminal.set(true);
            var task = reference.get();
            if (task != null) {
                ownedTasks.remove(task);
            }
        };
        Runnable scheduled = removeAfterRun
                ? () -> {
                    try {
                        runnable.run();
                    } finally {
                        onTerminal.run();
                    }
                }
                : runnable;
        var paperTask = scheduler.apply(scheduled, onTerminal);
        if (paperTask == null) {
            return SchedulerTask.noop();
        }
        var task = new PaperSchedulerTask(paperTask, onTerminal);
        ownedTasks.add(task);
        reference.set(task);
        if (terminal.get()) {
            ownedTasks.remove(task);
        }
        return task;
    }

    private SchedulerTask trackEntity(
            BiFunction<Runnable, Runnable, io.papermc.paper.threadedregions.scheduler.ScheduledTask> scheduler,
            Runnable runnable) {
        return trackPaper(scheduler, runnable, true);
    }

    private SchedulerTask scheduleAfterWorldLookup(
            TaskMetadata metadata, UUID worldId, Function<World, SchedulerTask> factory) {
        var lazy = new LazySchedulerTask();
        track(lazy);
        ownedLookupTasks.add(lazy);
        lazy.setupResult().whenComplete((_, _) -> {
            ownedLookupTasks.remove(lazy);
            ownedTasks.remove(lazy);
        });
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
        lazy.setupResult().whenComplete((_, _) -> {
            ownedLookupTasks.remove(lazy);
            ownedTasks.remove(lazy);
        });
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
