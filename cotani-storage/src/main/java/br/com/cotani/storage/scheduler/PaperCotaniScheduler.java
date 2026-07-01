package br.com.cotani.storage.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class PaperCotaniScheduler implements CotaniScheduler {

    private final Plugin plugin;

    public PaperCotaniScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void async(Runnable runnable) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, _ -> runnable.run());
    }

    @Override
    public void global(Runnable runnable) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, _ -> runnable.run());
    }

    @Override
    public void entity(Entity entity, Runnable runnable) {
        entity.getScheduler().run(plugin, _ -> runnable.run(), null);
    }

    @Override
    public void region(Location location, Runnable runnable) {
        plugin.getServer().getRegionScheduler().run(plugin, location, _ -> runnable.run());
    }

    @Override
    public void close() {
        plugin.getServer().getAsyncScheduler().cancelTasks(plugin);
        plugin.getServer().getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
