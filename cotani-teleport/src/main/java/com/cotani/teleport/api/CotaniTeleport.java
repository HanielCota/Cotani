package com.cotani.teleport.api;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CotaniTeleport {

    private static volatile @Nullable TeleportService teleportService;
    private static volatile @Nullable PendingTeleportService pendingTeleportService;

    private CotaniTeleport() {}

    /**
     * @deprecated Prefer holding a {@link TeleportModule} instance from {@code CotaniTeleports.create(...)}.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public static void init(TeleportService teleports, PendingTeleportService pendingTeleports) {
        Objects.requireNonNull(teleports, "teleports");
        Objects.requireNonNull(pendingTeleports, "pendingTeleports");
        if (teleportService != null || pendingTeleportService != null) {
            throw new IllegalStateException(
                    "CotaniTeleport is already initialized; call shutdown() before re-initializing.");
        }
        teleportService = teleports;
        pendingTeleportService = pendingTeleports;
    }

    /**
     * @deprecated Prefer {@link TeleportModule#teleportService()}.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public static TeleportService teleports() {
        TeleportService service = teleportService;
        if (service == null) {
            throw new IllegalStateException("CotaniTeleport ainda não foi inicializado.");
        }
        return service;
    }

    /**
     * @deprecated Prefer {@link TeleportModule#pendingTeleportService()}.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public static PendingTeleportService pendingTeleports() {
        PendingTeleportService service = pendingTeleportService;
        if (service == null) {
            throw new IllegalStateException("CotaniTeleport ainda não foi inicializado.");
        }
        return service;
    }

    /**
     * @deprecated Prefer checking whether your held {@link TeleportModule} reference is available.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public static boolean initialized() {
        return teleportService != null && pendingTeleportService != null;
    }

    /**
     * @deprecated Prefer closing the held {@link TeleportModule}.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    public static void shutdown() {
        teleportService = null;
        pendingTeleportService = null;
    }
}
