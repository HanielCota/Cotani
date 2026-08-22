package com.cotani.region.api;

/**
 * Protection and gameplay flags evaluated within 3D regions.
 */
public enum RegionFlag {
    /**
     * Player versus player combat allowed.
     */
    PVP,

    /**
     * Block breaking allowed.
     */
    BLOCK_BREAK,

    /**
     * Block placing allowed.
     */
    BLOCK_PLACE,

    /**
     * Chest and container opening allowed.
     */
    USE_CONTAINERS,

    /**
     * Door, trapdoor, and gate usage allowed.
     */
    USE_DOORS,

    /**
     * Dropping items on the ground allowed.
     */
    ITEM_DROP,

    /**
     * Picking up items from the ground allowed.
     */
    ITEM_PICKUP,

    /**
     * Player entry into region allowed (if denied, players are pushed back).
     */
    ENTRY,

    /**
     * Hostile and natural monster spawning allowed.
     */
    MOB_SPAWN,

    /**
     * TNT, creeper, and other entity explosions allowed.
     */
    EXPLOSIONS,

    /**
     * Fire spread and burning allowed.
     */
    FIRE_SPREAD
}
