package com.cotani.npc.internal;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.Npc;
import com.cotani.npc.api.NpcInteractEvent;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Event listener forwarding player quit and click interaction events to the NPC module.
 */
@InternalApi
public final class NpcPlayerListener implements Listener {

    private static final double MAX_INTERACT_DISTANCE_SQUARED = 20.25; // 4.5 blocks squared

    private final DefaultNpcModule module;

    public NpcPlayerListener(DefaultNpcModule module) {
        this.module = Objects.requireNonNull(module, "Parameter 'module' must not be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        module.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        var action = event.getAction();
        var player = event.getPlayer();

        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        var eyeLoc = player.getEyeLocation();
        var world = eyeLoc.getWorld();
        if (world == null) {
            return;
        }

        var cx = eyeLoc.getBlockX() >> 4;
        var cz = eyeLoc.getBlockZ() >> 4;
        var worldId = world.getUID() != null ? world.getUID() : new java.util.UUID(0L, 0L);
        var npcs = module.spatialIndex().getNearby(worldId, cx, cz, 1);
        if (npcs.isEmpty()) {
            return;
        }

        var eyeDir = eyeLoc.getDirection().normalize();

        Npc targetNpc = null;
        var closestDistance = Double.MAX_VALUE;

        for (var npc : npcs) {
            var npcLoc = npc.location();
            if (!Objects.equals(eyeLoc.getWorld(), npcLoc.getWorld())) {
                continue;
            }

            var distSq = eyeLoc.distanceSquared(npcLoc);
            if (distSq > MAX_INTERACT_DISTANCE_SQUARED) {
                continue;
            }

            if (isAimingAt(eyeLoc, eyeDir, npcLoc)) {
                if (distSq < closestDistance) {
                    closestDistance = distSq;
                    targetNpc = npc;
                }
            }
        }

        if (targetNpc != null) {
            var isLeft = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
            var clickAction = isLeft ? NpcInteractEvent.Action.LEFT_CLICK : NpcInteractEvent.Action.RIGHT_CLICK;
            var hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;

            var interactEvent = new NpcInteractEvent(player, targetNpc, hand, clickAction);
            try {
                targetNpc.interactionHandler().accept(interactEvent);
            } catch (Exception exception) {
                java.util.logging.Logger.getLogger(NpcPlayerListener.class.getName())
                        .log(java.util.logging.Level.WARNING, "NPC interaction callback failed", exception);
            }
        }
    }

    private static boolean isAimingAt(Location eyeLoc, org.bukkit.util.Vector eyeDir, Location npcLoc) {
        // NPC bounding box approximate center (head/chest at +1.0 Y)
        var npcCenter = npcLoc.clone().add(0, 1.0, 0);
        var toNpc = npcCenter.toVector().subtract(eyeLoc.toVector());
        if (toNpc.lengthSquared() < 0.0001) {
            return true;
        }
        var dot = toNpc.normalize().dot(eyeDir);

        // Dot product > 0.96 corresponds to within ~16 degrees of NPC center
        return dot > 0.96;
    }
}
