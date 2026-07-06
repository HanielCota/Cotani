package com.cotani.teleport.api;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("DeprecatedIsStillUsed")
public final class CotaniTeleport {

    private static final AtomicReference<@Nullable TeleportService> teleportService = new AtomicReference<>();
    private static final AtomicReference<@Nullable PendingTeleportService> pendingTeleportService =
            new AtomicReference<>();

    private CotaniTeleport() {}

    /**
     * @deprecated Prefer holding a {@link TeleportModule} instance from {@code CotaniTeleports.create(...)}.
     */
    @Deprecated(since = "1.0")
    public static void init(TeleportService teleports, PendingTeleportService pendingTeleports) {
        Objects.requireNonNull(teleports, "teleports");
        Objects.requireNonNull(pendingTeleports, "pendingTeleports");
        if (teleportService.get() != null || pendingTeleportService.get() != null) {
            throw new IllegalStateException(
                    "CotaniTeleport is already initialized; call shutdown() before re-initializing.");
        }
        teleportService.set(teleports);
        pendingTeleportService.set(pendingTeleports);
    }

    /**
     * @deprecated Prefer {@link TeleportModule#teleportService()}.
     */
    @Deprecated(since = "1.0")
    public static TeleportService teleports() {
        TeleportService service = teleportService.get();
        if (service == null) {
            throw new IllegalStateException("CotaniTeleport ainda não foi inicializado.");
        }
        return service;
    }

    /**
     * @deprecated Prefer {@link TeleportModule#pendingTeleportService()}.
     */
    @Deprecated(since = "1.0")
    public static PendingTeleportService pendingTeleports() {
        PendingTeleportService service = pendingTeleportService.get();
        if (service == null) {
            throw new IllegalStateException("CotaniTeleport ainda não foi inicializado.");
        }
        return service;
    }

    /**
     * @deprecated Prefer checking whether your held {@link TeleportModule} reference is available.
     */
    @Deprecated(since = "1.0")
    public static boolean initialized() {
        return teleportService.get() != null && pendingTeleportService.get() != null;
    }

    /**
     * @deprecated Prefer closing the held {@link TeleportModule}.
     */
    @Deprecated(since = "1.0")
    public static void shutdown() {
        teleportService.set(null);
        pendingTeleportService.set(null);
    }
}
