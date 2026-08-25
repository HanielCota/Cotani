package com.cotani.punishment.api;

/** Effective state of a punishment at a point in time, including its pre-creation state. */
public enum PunishmentStatus {
    NOT_STARTED,
    ACTIVE,
    EXPIRED,
    REVOKED
}
