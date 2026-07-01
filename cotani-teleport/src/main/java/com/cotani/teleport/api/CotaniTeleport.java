package com.cotani.teleport.api;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CotaniTeleport {
    private static volatile @Nullable TeleportService teleportService;
    private static volatile @Nullable PendingTeleportService pendingTeleportService;

    private CotaniTeleport() {}

    public static void init(TeleportService teleports, PendingTeleportService pendingTeleports) {
        teleportService = Objects.requireNonNull(teleports, "teleports");
        pendingTeleportService = Objects.requireNonNull(pendingTeleports, "pendingTeleports");
    }

    public static TeleportService teleports() {
        TeleportService service = teleportService;
        if (service == null) {
            throw new IllegalStateException("CotaniTeleport ainda não foi inicializado.");
        }
        return service;
    }

    public static PendingTeleportService pendingTeleports() {
        PendingTeleportService service = pendingTeleportService;
        if (service == null) {
            throw new IllegalStateException("CotaniTeleport ainda não foi inicializado.");
        }
        return service;
    }

    public static boolean initialized() {
        return teleportService != null && pendingTeleportService != null;
    }

    public static void shutdown() {
        teleportService = null;
        pendingTeleportService = null;
    }
}
