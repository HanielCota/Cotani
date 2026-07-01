package com.cotani.task.impl.scheduler;

import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.impl.executor.VirtualThreadExecutor;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class PlatformSchedulerFactory {

    private static final int DEFAULT_MAX_CONCURRENT = 256;

    private PlatformSchedulerFactory() {}

    public static PlatformScheduler create(Plugin plugin) {
        return create(plugin, DEFAULT_MAX_CONCURRENT, true);
    }

    public static PlatformScheduler create(Plugin plugin, int maxConcurrentVirtualThreads) {
        return create(plugin, maxConcurrentVirtualThreads, true);
    }

    public static PlatformScheduler create(Plugin plugin, int maxConcurrentVirtualThreads, boolean useVirtualThreads) {
        Objects.requireNonNull(plugin, "plugin");

        var executor = VirtualThreadExecutor.create(maxConcurrentVirtualThreads, useVirtualThreads);

        return new PaperPlatformScheduler(plugin, executor);
    }
}
