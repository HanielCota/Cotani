package com.cotani.teleport.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@SuppressWarnings("NullAway")
class TeleportRequestTest {

    @Test
    void targetIsClonedOnConstruction() {
        World world = Mockito.mock(World.class);
        Location original = new Location(world, 0, 64, 0);
        TeleportRequest request = TeleportRequest.builder()
                .playerId(UUID.randomUUID())
                .target(original)
                .cause(TeleportCause.PLUGIN_INTERNAL)
                .build();

        original.setX(100);

        assertEquals(0, request.target().getX());
    }

    @Test
    void builderProducesDefensiveCopy() {
        World world = Mockito.mock(World.class);
        Location original = new Location(world, 0, 64, 0);
        TeleportRequest request = TeleportRequest.builder()
                .playerId(UUID.randomUUID())
                .target(original)
                .cause(TeleportCause.PLUGIN_INTERNAL)
                .build();

        original.setX(100);

        assertEquals(0, request.target().getX());
    }

    @Test
    void sourceDefaultsToUnknownWhenBlank() {
        TeleportRequest request = TeleportRequest.builder()
                .playerId(UUID.randomUUID())
                .target(new Location(Mockito.mock(World.class), 0, 64, 0))
                .cause(TeleportCause.PLUGIN_INTERNAL)
                .source("   ")
                .build();

        assertEquals("unknown", request.source());
    }

    @Test
    void sourcePreservesNonBlankValue() {
        TeleportRequest request = TeleportRequest.builder()
                .playerId(UUID.randomUUID())
                .target(new Location(Mockito.mock(World.class), 0, 64, 0))
                .cause(TeleportCause.PLUGIN_INTERNAL)
                .source("home")
                .build();

        assertEquals("home", request.source());
    }
}
