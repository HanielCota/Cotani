package com.cotani.region.impl;

import com.cotani.api.InternalApi;
import com.cotani.region.api.RegionEnterEvent;
import com.cotani.region.api.RegionFlag;
import com.cotani.region.api.RegionLeaveEvent;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event listener enforcing protection flags and triggering region transition events.
 */
@InternalApi
public final class RegionProtectionListener implements Listener {

    private final DefaultRegionModule module;
    private final Map<UUID, Set<String>> playerRegions = new ConcurrentHashMap<>();

    public RegionProtectionListener(DefaultRegionModule module) {
        this.module = Objects.requireNonNull(module, "Parameter 'module' must not be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerRegions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var blockLoc = event.getBlock().getLocation();
        if (!module.isFlagAllowed(blockLoc, RegionFlag.BLOCK_BREAK, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        var blockLoc = event.getBlock().getLocation();
        if (!module.isFlagAllowed(blockLoc, RegionFlag.BLOCK_PLACE, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            var loc = event.getEntity().getLocation();
            if (loc != null && !module.isFlagAllowed(loc, RegionFlag.PVP, true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        var loc = event.getPlayer().getLocation();
        if (loc != null && !module.isFlagAllowed(loc, RegionFlag.ITEM_DROP, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            var loc = event.getEntity().getLocation();
            if (loc != null && !module.isFlagAllowed(loc, RegionFlag.ITEM_PICKUP, true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        var clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        var loc = clickedBlock.getLocation();
        var mat = clickedBlock.getType();

        if (isContainer(mat)) {
            if (!module.isFlagAllowed(loc, RegionFlag.USE_CONTAINERS, true)) {
                event.setCancelled(true);
            }
        } else if (isDoor(mat)) {
            if (!module.isFlagAllowed(loc, RegionFlag.USE_DOORS, true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();

        if (to == null
                || (from.getBlockX() == to.getBlockX()
                        && from.getBlockY() == to.getBlockY()
                        && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        var player = event.getPlayer();
        var playerId = player.getUniqueId();

        var currentRegions = module.regionsAt(to);
        var currentRegionIds = new HashSet<String>();
        for (var r : currentRegions) {
            currentRegionIds.add(r.id());
        }

        var previousRegionIds = playerRegions.computeIfAbsent(playerId, _ -> new HashSet<>());

        // Check enter
        for (var region : currentRegions) {
            if (!previousRegionIds.contains(region.id())) {
                // Entered region
                var enterEvent = new RegionEnterEvent(player, region, from, to);
                module.handleRegionEnter(enterEvent);

                if (region.greetingMessage() != null) {
                    player.sendMessage(region.greetingMessage());
                }
            }
        }

        // Check leave
        for (var prevId : previousRegionIds) {
            if (!currentRegionIds.contains(prevId)) {
                var optRegion = module.findRegion(prevId);
                if (optRegion.isPresent()) {
                    var region = optRegion.get();
                    var leaveEvent = new RegionLeaveEvent(player, region, from, to);
                    module.handleRegionLeave(leaveEvent);

                    if (region.farewellMessage() != null) {
                        player.sendMessage(region.farewellMessage());
                    }
                }
            }
        }

        playerRegions.put(playerId, currentRegionIds);
    }

    private static boolean isContainer(Material material) {
        var name = material.name();
        return name.endsWith("CHEST")
                || name.endsWith("BARREL")
                || name.endsWith("SHULKER_BOX")
                || name.equals("HOPPER")
                || name.equals("DROPPER")
                || name.equals("DISPENSER");
    }

    private static boolean isDoor(Material material) {
        var name = material.name();
        return name.endsWith("DOOR") || name.endsWith("TRAPDOOR") || name.endsWith("GATE");
    }
}
