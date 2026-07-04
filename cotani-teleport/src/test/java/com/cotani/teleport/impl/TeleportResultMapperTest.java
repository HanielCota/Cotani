package com.cotani.teleport.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cotani.teleport.api.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeleportResultMapperTest {

    private TeleportResultMapper mapper;

    private static TeleportContext createContext() {
        var location = org.mockito.Mockito.mock(Location.class);
        org.mockito.Mockito.when(location.clone()).thenReturn(location);
        return new TeleportContext(
                UUID.randomUUID(),
                location,
                location,
                TeleportCause.PLUGIN_INTERNAL,
                TeleportOptions.defaults(),
                "test",
                Instant.now());
    }

    @BeforeEach
    void setUp() {
        var notifier = org.mockito.Mockito.mock(TeleportEventNotifier.class);
        org.mockito.Mockito.when(notifier.fireFailure(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        org.mockito.Mockito.when(notifier.elapsedMillis(org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);
        mapper = new TeleportResultMapper(notifier);
    }

    @Test
    void mapExceptionUnwrapsCompletionException() {
        var context = createContext();
        var cause = new CompletionException(new TimeoutException("timed out"));
        var result = mapper.mapException(context, cause).toCompletableFuture().join();
        assertInstanceOf(TeleportResult.Failure.class, result);
        assertEquals(TeleportFailureReason.TIMEOUT, ((TeleportResult.Failure) result).reason());
    }

    @Test
    void mapExceptionMapsUnknownError() {
        var context = createContext();
        var result = mapper.mapException(context, new RuntimeException("weird"))
                .toCompletableFuture()
                .join();
        assertInstanceOf(TeleportResult.Failure.class, result);
        assertEquals(TeleportFailureReason.UNKNOWN_ERROR, ((TeleportResult.Failure) result).reason());
    }
}
