package com.cotani.teleport.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class CotaniTeleportTest {

    @AfterEach
    void tearDown() {
        CotaniTeleport.shutdown();
    }

    @Test
    void initSetsServices() {
        var teleportService = org.mockito.Mockito.mock(TeleportService.class);
        var pendingService = org.mockito.Mockito.mock(PendingTeleportService.class);
        CotaniTeleport.init(teleportService, pendingService);
        assertTrue(CotaniTeleport.initialized());
        assertSame(teleportService, CotaniTeleport.teleports());
        assertSame(pendingService, CotaniTeleport.pendingTeleports());
    }

    @Test
    void initRejectsReInit() {
        var teleportService = org.mockito.Mockito.mock(TeleportService.class);
        var pendingService = org.mockito.Mockito.mock(PendingTeleportService.class);
        CotaniTeleport.init(teleportService, pendingService);
        assertThrows(IllegalStateException.class, () -> CotaniTeleport.init(teleportService, pendingService));
    }

    @Test
    void initAllowsReInitAfterShutdown() {
        var teleportService = org.mockito.Mockito.mock(TeleportService.class);
        var pendingService = org.mockito.Mockito.mock(PendingTeleportService.class);
        CotaniTeleport.init(teleportService, pendingService);
        CotaniTeleport.shutdown();
        assertDoesNotThrow(() -> CotaniTeleport.init(teleportService, pendingService));
    }

    @Test
    void teleportsThrowsWhenNotInitialized() {
        assertThrows(IllegalStateException.class, CotaniTeleport::teleports);
    }

    @Test
    void shutdownClearsServices() {
        var teleportService = org.mockito.Mockito.mock(TeleportService.class);
        var pendingService = org.mockito.Mockito.mock(PendingTeleportService.class);
        CotaniTeleport.init(teleportService, pendingService);
        CotaniTeleport.shutdown();
        assertFalse(CotaniTeleport.initialized());
    }
}
