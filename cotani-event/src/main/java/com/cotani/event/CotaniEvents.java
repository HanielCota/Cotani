package com.cotani.event;

import com.cotani.event.api.EventBus;
import com.cotani.event.bus.DefaultEventBus;
import com.cotani.event.exception.LoggingEventExceptionHandler;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.bukkit.plugin.Plugin;

/**
 * Entrypoint factory for the {@code cotani-event} module.
 */
public final class CotaniEvents {

    private CotaniEvents() {}

    /**
     * Creates an {@link EventBus} instance for the given plugin.
     *
     * <p>The returned bus owns its internal executors and shuts them down on {@link EventBus#close()}.
     *
     * @param plugin owning plugin
     * @return event bus instance
     */
    public static EventBus create(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return DefaultEventBus.createOwning(
                new LoggingEventExceptionHandler(plugin.getLogger()), Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates an {@link EventBus} instance with a custom async executor.
     *
     * <p>The executor remains owned by the caller and is never shut down by the returned bus.
     *
     * @param plugin owning plugin
     * @param asyncExecutor async execution thread pool
     * @return event bus instance
     */
    public static EventBus create(Plugin plugin, Executor asyncExecutor) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        return DefaultEventBus.create(new LoggingEventExceptionHandler(plugin.getLogger()), asyncExecutor);
    }
}
