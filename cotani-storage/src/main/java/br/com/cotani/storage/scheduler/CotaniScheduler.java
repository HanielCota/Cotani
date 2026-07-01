package br.com.cotani.storage.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface CotaniScheduler extends AutoCloseable {

    void async(Runnable runnable);

    void global(Runnable runnable);

    void entity(Entity entity, Runnable runnable);

    void region(Location location, Runnable runnable);

    @Override
    void close();
}
