package com.cotani.gui;

import java.time.Duration;
import org.bukkit.plugin.Plugin;

/**
 * Entrypoint factory for the {@code cotani-gui} module.
 */
public final class CotaniGuis {

    private CotaniGuis() {}

    /**
     * Creates and registers the GUI module with the default debounce interval.
     *
     * @param plugin owning plugin
     * @return initialized GUI module
     */
    public static CotaniGuiModule create(Plugin plugin) {
        return CotaniGuiModule.create(plugin);
    }

    /**
     * Creates and registers the GUI module with a custom debounce interval.
     *
     * @param plugin owning plugin
     * @param debounce click debounce duration
     * @return initialized GUI module
     */
    public static CotaniGuiModule create(Plugin plugin, Duration debounce) {
        return CotaniGuiModule.create(plugin, debounce);
    }
}
