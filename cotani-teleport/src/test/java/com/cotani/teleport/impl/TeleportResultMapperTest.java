package com.cotani.teleport.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cotani.teleport.api.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TeleportResultMapperTest {

    private TeleportResultMapper mapper;

    private static TeleportContext createContext() {
        var location = Mockito.mock(Location.class);
        Mockito.when(location.clone()).thenReturn(location);
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
        var notifier = Mockito.mock(TeleportEventNotifier.class);
        Mockito.when(notifier.fireFailure(ArgumentMatchers.any())).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(notifier.elapsedMillis(ArgumentMatchers.any())).thenReturn(0L);
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
