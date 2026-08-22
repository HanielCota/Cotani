package com.cotani.npc.api;

/**
 * Poses that can be assumed by a virtual NPC.
 */
public enum NpcPose {
    /**
     * Standard standing upright pose.
     */
    STANDING,

    /**
     * Sneaking or crouching pose.
     */
    CROUCHING,

    /**
     * Sitting pose.
     */
    SITTING,

    /**
     * Sleeping pose (lying down).
     */
    SLEEPING,

    /**
     * Swimming horizontal glide pose.
     */
    SWIMMING,

    /**
     * Spinning riptide attack animation pose.
     */
    SPIN_ATTACK
}
