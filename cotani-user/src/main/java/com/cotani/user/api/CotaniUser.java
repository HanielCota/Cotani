package com.cotani.user.api;

import java.util.UUID;

/**
 * Public read-only view of a loaded Cotani user.
 */
public interface CotaniUser {

    UUID uniqueId();

    String username();

    long firstJoinAt();

    long lastJoinAt();

    long lastQuitAt();
}
