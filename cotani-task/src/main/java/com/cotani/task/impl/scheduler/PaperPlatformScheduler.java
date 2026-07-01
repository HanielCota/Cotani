package com.cotani.task.impl.scheduler;

import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.impl.executor.VirtualThreadExecutor;
import com.cotani.task.impl.task.FutureSchedulerTask;
import com.cotani.task.impl.task.PaperSchedulerTask;
import java.time.Duration;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class PaperPlatformScheduler implements PlatformScheduler, AutoCloseable {

    private final Plugin plugin;
    private final VirtualThreadExecutor virtualThreadExecutor;

    public PaperPlatformScheduler(Plugin plugin, VirtualThreadExecutor virtualThreadExecutor) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.virtualThreadExecutor = Objects.requireNonNull(virtualThreadExecutor, "virtualThreadExecutor");
    }

    @Override
    public SchedulerTask runAsync(TaskMetadata metadata, Runnable runnable) {
        return new FutureSchedulerTask(virtualThreadExecutor.submit(metadata, runnable));
    }

    @Override
    public SchedulerTask runAsyncLater(TaskMetadata metadata, Runnable runnable, Duration delay) {
        return new FutureSchedulerTask(virtualThreadExecutor.schedule(metadata, runnable, delay.toMillis()));
    }

    @Override
    public SchedulerTask runAsyncTimer(
            TaskMetadata metadata, Runnable runnable, Duration initialDelay, Duration period) {
        return new FutureSchedulerTask(virtualThreadExecutor.scheduleAtFixedRate(
                metadata, runnable, initialDelay.toMillis(), period.toMillis()));
    }

    @Override
    public SchedulerTask runGlobal(TaskMetadata metadata, Runnable runnable) {
        var task = Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> runnable.run());

        return new PaperSchedulerTask(task);
    }

    @Override
    public SchedulerTask runGlobalLater(TaskMetadata metadata, Runnable runnable, Duration delay) {
        var task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> runnable.run(), Ticks.from(delay));

        return new PaperSchedulerTask(task);
    }

    @Override
    public SchedulerTask runGlobalTimer(
            TaskMetadata metadata, Runnable runnable, Duration initialDelay, Duration period) {
        var task = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, ignored -> runnable.run(), Ticks.from(initialDelay), Ticks.from(period));

        return new PaperSchedulerTask(task);
    }

    @Override
    public SchedulerTask runRegion(TaskMetadata metadata, Location location, Runnable runnable) {
        var task = Bukkit.getRegionScheduler().run(plugin, location, ignored -> runnable.run());

        return new PaperSchedulerTask(task);
    }

    @Override
    public SchedulerTask runRegionLater(TaskMetadata metadata, Location location, Runnable runnable, Duration delay) {
        var task =
                Bukkit.getRegionScheduler().runDelayed(plugin, location, ignored -> runnable.run(), Ticks.from(delay));

        return new PaperSchedulerTask(task);
    }

    @Override
    public SchedulerTask runRegionTimer(
            TaskMetadata metadata, Location location, Runnable runnable, Duration initialDelay, Duration period) {
        var task = Bukkit.getRegionScheduler()
                .runAtFixedRate(
                        plugin, location, ignored -> runnable.run(), Ticks.from(initialDelay), Ticks.from(period));

        return new PaperSchedulerTask(task);
    }

    @Override
    public SchedulerTask runEntity(TaskMetadata metadata, Entity entity, Runnable runnable, Runnable retired) {
        var task = entity.getScheduler().run(plugin, ignored -> runnable.run(), retired);

        if (task == null) {
            return SchedulerTask.noop();
        }

        return new PaperSchedulerTask(task);
    }

    @Override
    public SchedulerTask runEntityLater(
            TaskMetadata metadata, Entity entity, Runnable runnable, Runnable retired, Duration delay) {
        var task = entity.getScheduler().runDelayed(plugin, ignored -> runnable.run(), retired, Ticks.from(delay));

        if (task == null) {
            return SchedulerTask.noop();
        }

        return new PaperSchedulerTask(task);
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
            return SchedulerTask.noop();
        }

        return new PaperSchedulerTask(task);
    }

    @Override
    public void cancelOwnedTasks() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }

    @Override
    public void close() {
        cancelOwnedTasks();
        virtualThreadExecutor.close();
    }
}
