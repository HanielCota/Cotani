package com.cotani.cleanup.api;

/** Pure protection policy evaluated from immutable entity snapshots. */
@FunctionalInterface
public interface CleanupProtection {
    boolean isProtected(CleanupEntitySnapshot entity);

    static CleanupProtection none() {
        return entity -> false;
    }
}
