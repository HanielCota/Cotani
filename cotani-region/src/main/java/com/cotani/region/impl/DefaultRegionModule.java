package com.cotani.region.impl;

import com.cotani.api.InternalApi;
import com.cotani.region.api.Region3D;
import com.cotani.region.api.RegionEnterEvent;
import com.cotani.region.api.RegionFlag;
import com.cotani.region.api.RegionLeaveEvent;
import com.cotani.region.api.RegionModule;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/**
 * Default implementation of {@link RegionModule}.
 */
@InternalApi
public final class DefaultRegionModule implements RegionModule {

    private final PaperTaskScheduler scheduler;
    private final RegionSpatialGrid spatialGrid;
    private final RegionProtectionListener listener;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultRegionModule(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
        this.spatialGrid = new RegionSpatialGrid();
        this.listener = new RegionProtectionListener(this);

        var server = plugin.getServer();
        if (server != null) {
            server.getPluginManager().registerEvents(listener, plugin);
        }
    }

    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public void registerRegion(Region3D region) {
        Objects.requireNonNull(region, "Parameter 'region' must not be null");
        if (closed.get()) {
            return;
        }

        spatialGrid.add(region);
    }

    @Override
    public boolean unregisterRegion(String regionId) {
        Objects.requireNonNull(regionId, "Parameter 'regionId' must not be null");
        if (closed.get()) {
            return false;
        }

        return spatialGrid.remove(regionId);
    }

    @Override
    public Optional<Region3D> findRegion(String regionId) {
        Objects.requireNonNull(regionId, "Parameter 'regionId' must not be null");
        return spatialGrid.find(regionId);
    }

    @Override
    public Collection<Region3D> allRegions() {
        return spatialGrid.all();
    }

    @Override
    public List<Region3D> regionsAt(Location location) {
        Objects.requireNonNull(location, "Parameter 'location' must not be null");
        return spatialGrid.regionsAt(location);
    }

    @Override
    public Optional<Region3D> highestPriorityRegionAt(Location location) {
        Objects.requireNonNull(location, "Parameter 'location' must not be null");

        var regions = spatialGrid.regionsAt(location);
        return regions.isEmpty() ? Optional.empty() : Optional.of(regions.getFirst());
    }

    @Override
    public boolean isFlagAllowed(Location location, RegionFlag flag, boolean defaultValue) {
        Objects.requireNonNull(location, "Parameter 'location' must not be null");
        Objects.requireNonNull(flag, "Parameter 'flag' must not be null");

        var regions = spatialGrid.regionsAt(location);
        for (var region : regions) {
            var flagVal = region.getFlag(flag);
            if (flagVal.isPresent()) {
                return flagVal.get();
            }
        }
        return defaultValue;
    }

    public void handleRegionEnter(RegionEnterEvent event) {
        Objects.requireNonNull(event, "Parameter 'event' must not be null");
        // Dispatched on viewer entity scheduler
    }

    public void handleRegionLeave(RegionLeaveEvent event) {
        Objects.requireNonNull(event, "Parameter 'event' must not be null");
        // Dispatched on viewer entity scheduler
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        HandlerList.unregisterAll(listener);
        spatialGrid.clear();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        closeAsync();
    }
}
