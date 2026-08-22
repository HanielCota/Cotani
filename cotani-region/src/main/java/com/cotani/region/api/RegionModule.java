package com.cotani.region.api;

import com.cotani.AsyncCloseable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;

/**
 * Service contract for managing 3D regions, spatial queries, and protection flags.
 */
public interface RegionModule extends AutoCloseable, AsyncCloseable {

    /**
     * Registers a 3D region.
     *
     * @param region region to register
     */
    void registerRegion(Region3D region);

    /**
     * Unregisters a 3D region by its unique identifier.
     *
     * @param regionId unique region ID
     * @return true if region was removed
     */
    boolean unregisterRegion(String regionId);

    /**
     * Finds a region by ID.
     *
     * @param regionId unique region ID
     * @return Optional containing the region, or empty
     */
    Optional<Region3D> findRegion(String regionId);

    /**
     * Returns an unmodifiable snapshot of all registered regions.
     *
     * @return collection of all regions
     */
    Collection<Region3D> allRegions();

    /**
     * Finds all regions containing the specified location, ordered from highest priority to lowest.
     *
     * @param location location to query
     * @return list of matching regions sorted by descending priority
     */
    List<Region3D> regionsAt(Location location);

    /**
     * Returns the highest priority region containing the location, if any.
     *
     * @param location location to query
     * @return Optional containing the highest priority region, or empty
     */
    Optional<Region3D> highestPriorityRegionAt(Location location);

    /**
     * Evaluates whether a flag is allowed at the given location based on region priority.
     *
     * @param location location to evaluate
     * @param flag flag to check
     * @param defaultValue default outcome if no region defines this flag
     * @return true if allowed, false if denied
     */
    boolean isFlagAllowed(Location location, RegionFlag flag, boolean defaultValue);

    @Override
    void close();
}
