package com.cotani.gui;

import com.cotani.gui.safety.AntiExploitGuard;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/**
 * Module bootstrap: registers the GUI safety listeners and owns their lifecycle.
 *
 * <p>Create one instance per plugin in {@code onEnable} and close it on shutdown, for example via
 * {@code Cotani.forPlugin(plugin).with(CotaniGuiModule.create(plugin))}.
 */
public final class CotaniGuiModule implements AutoCloseable {

    /**
     * Default per-player click debounce (100ms).
     */
    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(100);

    private final AntiExploitGuard guard;
    private final Duration debounce;
    private final AtomicBoolean closed = new AtomicBoolean();

    private CotaniGuiModule(AntiExploitGuard guard, Duration debounce) {
        this.guard = guard;
        this.debounce = debounce;
    }

    /**
     * Creates and registers the module with the {@link #DEFAULT_DEBOUNCE} interval.
     *
     * @param plugin the owning plugin
     * @return the registered module
     */
    public static CotaniGuiModule create(Plugin plugin) {
        return create(plugin, DEFAULT_DEBOUNCE);
    }

    /**
     * Creates and registers the module with a custom click debounce.
     *
     * @param plugin the owning plugin
     * @param debounce the minimum interval between accepted clicks per player
     * @return the registered module
     */
    public static CotaniGuiModule create(Plugin plugin, Duration debounce) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        Objects.requireNonNull(debounce, "Parameter 'debounce' must not be null");

        var module = new CotaniGuiModule(new AntiExploitGuard(debounce), debounce);
        plugin.getServer().getPluginManager().registerEvents(module.guard, plugin);
        return module;
    }

    /**
     * Returns the configured click debounce.
     *
     * @return the debounce interval
     */
    public Duration debounce() {
        return debounce;
    }

    /**
     * Unregisters the listeners and clears debounce state. Closing twice is a no-op.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        HandlerList.unregisterAll(guard);
        guard.clearState();
    }
}
