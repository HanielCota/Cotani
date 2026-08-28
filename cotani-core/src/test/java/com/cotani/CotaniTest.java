package com.cotani;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CotaniTest {
    private static Plugin pluginWithLogger() {
        var plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("cotani-test"));

        return plugin;
    }

    private static void setBukkitServer(@Nullable Server server) {
        try {
            var field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to override Bukkit.server", failure);
        }
    }

    private static CotaniCloseException awaitFailure(Cotani cotani) {
        var completion = assertThrows(
                CompletionException.class,
                () -> cotani.closeAsync().toCompletableFuture().join());
        return assertInstanceOf(CotaniCloseException.class, completion.getCause());
    }

    @AfterEach
    void restoreBukkitServer() {
        setBukkitServer(null);
    }

    @Test
    @Tag("stress")
    void closesThousandsOfRegisteredResourcesExactlyOnceInOneLifecycle() {
        var plugin = pluginWithLogger();
        var closed = new AtomicInteger();
        var builder = Cotani.forPlugin(plugin);
        for (int index = 0; index < StressTestSupport.iterations(); index++) {
            builder.with(closed::incrementAndGet);
        }
        var cotani = builder.build();

        var firstClose = cotani.closeAsync();
        var repeatedClose = cotani.closeAsync();
        CompletableFuture.allOf(firstClose.toCompletableFuture(), repeatedClose.toCompletableFuture())
                .join();

        assertSame(firstClose, repeatedClose);
        assertEquals(StressTestSupport.iterations(), closed.get());
    }

    @Test
    void forPluginReturnsBuilder() {
        var plugin = pluginWithLogger();

        var builder = Cotani.forPlugin(plugin);

        assertSame(plugin, builder.build().plugin());
    }

    @Test
    @SuppressWarnings("NullAway")
    void forPluginRejectsNull() {
        assertThrows(NullPointerException.class, () -> Cotani.forPlugin(null));
    }

    @Test
    void closeClosesRegisteredResources() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        cotani.close();

        assertTrue(closed.get());
    }

    @Test
    void closeClosesResourcesInReverseOrder() {
        var plugin = pluginWithLogger();
        var order = new ArrayList<String>();
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> order.add("first"))
                .with(() -> order.add("second"))
                .build();

        cotani.close();

        assertEquals(List.of("second", "first"), order);
    }

    @Test
    void closeAggregatesExceptions() {
        var plugin = pluginWithLogger();
        var first = new RuntimeException("first");
        var second = new RuntimeException("second");
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> {
                    throw first;
                })
                .with(() -> {
                    throw second;
                })
                .build();

        var thrown = awaitFailure(cotani);
        var cause = thrown.getCause();

        assertEquals("Failed to close resource", thrown.getMessage());
        assertEquals("second", cause != null ? cause.getMessage() : "");
        assertEquals("first", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void closeWrapsCheckedExceptions() {
        var plugin = pluginWithLogger();
        AutoCloseable closeable = () -> {
            throw new Exception("checked");
        };
        var cotani = Cotani.forPlugin(plugin).with(closeable).build();

        var thrown = awaitFailure(cotani);
        var cause = thrown.getCause();

        assertEquals("Failed to close resource", thrown.getMessage());
        assertEquals("checked", cause == null ? "" : cause.getMessage());
    }

    @Test
    void closeContinuesAfterException() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> closed.set(true))
                .with(() -> {
                    throw new RuntimeException("boom");
                })
                .build();

        awaitFailure(cotani);
        assertTrue(closed.get());
    }

    @Test
    void closeIsIdempotent() {
        var plugin = pluginWithLogger();
        var count = new AtomicInteger();
        var cotani = Cotani.forPlugin(plugin).with(count::incrementAndGet).build();

        cotani.close();
        cotani.close();

        assertEquals(1, count.get());
    }

    @Test
    void closeWithNoResourcesDoesNotThrow() {
        var plugin = pluginWithLogger();

        assertDoesNotThrow(Cotani.forPlugin(plugin).build()::close);
    }

    @Test
    void registerAddsResourceClosedByClose() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).build();

        cotani.register(() -> closed.set(true));
        cotani.close();

        assertTrue(closed.get());
    }

    @Test
    void registerAfterCloseThrows() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin).build();
        cotani.close();

        assertThrows(IllegalStateException.class, () -> cotani.register(() -> {}));
    }

    @Test
    @SuppressWarnings("NullAway")
    void registerNullRejects() {
        var plugin = pluginWithLogger();

        assertThrows(
                NullPointerException.class,
                () -> Cotani.forPlugin(plugin).build().register(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void deregisterNullRejects() {
        var plugin = pluginWithLogger();

        assertThrows(
                NullPointerException.class,
                () -> Cotani.forPlugin(plugin).build().deregister(null));
    }

    @Test
    void deregisterAfterCloseDoesNotThrow() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin).build();
        cotani.close();

        assertDoesNotThrow(() -> cotani.deregister(() -> {}));
    }

    @Test
    void deregisterUnknownResourceIsSafe() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        cotani.deregister(() -> {});
        cotani.close();

        assertTrue(closed.get());
    }

    @Test
    void builderIsSingleUse() {
        var plugin = pluginWithLogger();
        var builder = Cotani.forPlugin(plugin).with(() -> {});

        builder.build();

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void builderWithAfterBuildThrows() {
        var plugin = pluginWithLogger();
        var builder = Cotani.forPlugin(plugin).with(() -> {});

        builder.build();

        assertThrows(IllegalStateException.class, () -> builder.with(() -> {}));
    }

    @Test
    void builderWithAsyncAfterBuildThrows() {
        var plugin = pluginWithLogger();
        var builder = Cotani.forPlugin(plugin).withAsync(CompletableFuture::new);

        builder.build();

        assertThrows(IllegalStateException.class, () -> builder.withAsync(CompletableFuture::new));
    }

    @Test
    @SuppressWarnings("NullAway")
    void builderWithAsyncRejectsNull() {
        var plugin = pluginWithLogger();

        assertThrows(NullPointerException.class, () -> Cotani.forPlugin(plugin).withAsync(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void builderWithRejectsNull() {
        var plugin = pluginWithLogger();

        assertThrows(NullPointerException.class, () -> Cotani.forPlugin(plugin).with(null));
    }

    @Test
    void failureCarriesCauseAndSuppressed() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> {
                    throw new IllegalStateException("a");
                })
                .with(() -> {
                    throw new IllegalStateException("b");
                })
                .build();

        var thrown = awaitFailure(cotani);

        assertNotNull(thrown.getCause());
        assertEquals(1, thrown.getSuppressed().length);
        assertNotEquals(thrown.getCause(), thrown.getSuppressed()[0]);
    }

    @Test
    void deregisterRemovesResource() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        AutoCloseable resource = () -> closed.set(true);
        var cotani = Cotani.forPlugin(plugin).with(resource).build();

        cotani.deregister(resource);
        cotani.close();

        assertFalse(closed.get());
    }

    @Test
    void deregisterRemovesRegisteredAsyncCloseable() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        class AsyncResource implements AutoCloseable, AsyncCloseable {
            @Override
            public CompletionStage<Void> closeAsync() {
                closed.set(true);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void close() {}
        }
        AutoCloseable resource = new AsyncResource();
        var cotani = Cotani.forPlugin(plugin).build();
        cotani.register(resource);

        cotani.deregister(resource);
        cotani.close();

        assertFalse(closed.get());
    }

    @Test
    void closeAsyncExecutesAsyncCloseables() {
        var plugin = pluginWithLogger();
        var asyncExecuted = new AtomicBoolean();
        var syncExecuted = new AtomicBoolean();

        var cotani = Cotani.forPlugin(plugin)
                .with(() -> syncExecuted.set(true))
                .withAsync(() -> {
                    asyncExecuted.set(true);
                    return CompletableFuture.completedFuture(null);
                })
                .build();

        cotani.closeAsync().toCompletableFuture().join();

        assertTrue(asyncExecuted.get());
        assertTrue(syncExecuted.get());
    }

    @Test
    void concurrentCloseAsyncCallsShareCompletion() {
        var plugin = pluginWithLogger();
        var closeGate = new CompletableFuture<Void>();
        var cotani = Cotani.forPlugin(plugin).withAsync(() -> closeGate).build();

        var first = cotani.closeAsync();
        var second = cotani.closeAsync();

        assertSame(first, second);
        assertFalse(first.toCompletableFuture().isDone());
        closeGate.complete(null);
        assertDoesNotThrow(() -> first.toCompletableFuture().join());
    }

    @Test
    void registerAsyncAndDeregisterAsync() {
        var plugin = pluginWithLogger();
        var executed = new AtomicBoolean();
        Supplier<CompletionStage<Void>> asyncSupplier = () -> {
            executed.set(true);
            return CompletableFuture.completedFuture(null);
        };

        var cotani = Cotani.forPlugin(plugin).build();
        cotani.registerAsync(asyncSupplier);
        cotani.deregisterAsync(asyncSupplier);
        cotani.close();

        assertFalse(executed.get());
    }

    @Test
    void registerAsyncAfterCloseThrows() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin).build();
        cotani.close();

        assertThrows(IllegalStateException.class, () -> cotani.registerAsync(CompletableFuture::new));
    }

    @Test
    void deregisterAsyncAfterCloseDoesNotThrow() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin).build();
        cotani.close();

        assertDoesNotThrow(() -> cotani.deregisterAsync(CompletableFuture::new));
    }

    @Test
    void registerAfterCloseAsyncThrows() {
        var plugin = pluginWithLogger();
        var gate = new CompletableFuture<Void>();
        var cotani = Cotani.forPlugin(plugin).withAsync(() -> gate).build();

        cotani.closeAsync();

        assertThrows(IllegalStateException.class, () -> cotani.register(() -> {}));
        gate.complete(null);
    }

    @Test
    void concurrentCloseAndCloseAsyncRunTeardownOnce() throws InterruptedException {
        var plugin = pluginWithLogger();
        var teardownCount = new AtomicInteger();
        var gate = new CompletableFuture<Void>();
        var cotani = Cotani.forPlugin(plugin)
                .withAsync(() -> {
                    teardownCount.incrementAndGet();
                    return gate;
                })
                .build();

        var asyncStage = cotani.closeAsync();
        var closeError = new AtomicReference<Throwable>();
        var closeThread = new Thread(() -> {
            try {
                cotani.close();
            } catch (RuntimeException | Error failure) {
                closeError.set(failure);
            }
        });

        closeThread.start();
        gate.complete(null);
        closeThread.join(5000);

        assertFalse(closeThread.isAlive());
        assertNull(closeError.get());
        assertDoesNotThrow(() -> asyncStage.toCompletableFuture().join());
        assertEquals(1, teardownCount.get());
    }

    @Test
    void asyncSupplierExceptionIsAggregated() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin)
                .withAsync(() -> {
                    throw new RuntimeException("Async supplier boom");
                })
                .build();

        var thrown = awaitFailure(cotani);
        assertEquals("Failed to close resource", thrown.getMessage());
        assertNotNull(thrown.getCause());
        assertEquals("Async supplier boom", thrown.getCause().getMessage());
    }

    @Test
    @SuppressWarnings("NullAway")
    void registerAsyncNullRejects() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin).build();

        assertThrows(NullPointerException.class, () -> cotani.registerAsync(null));
    }

    @Test
    void asyncStageFailureIsAggregated() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin)
                .withAsync(() -> {
                    var failed = new CompletableFuture<Void>();
                    failed.completeExceptionally(new IllegalStateException("stage boom"));

                    return failed;
                })
                .build();

        var thrown = awaitFailure(cotani);

        assertEquals("Failed to close resource", thrown.getMessage());
        assertNotNull(thrown.getCause());
        assertEquals("stage boom", thrown.getCause().getMessage());
    }

    @Test
    void closePreservesAsyncCloseableFailureAsCause() {
        var plugin = pluginWithLogger();
        var expected = new CotaniCloseException("async closeable boom", new IllegalStateException("root"));
        var cotani = Cotani.forPlugin(plugin)
                .withAsync(() -> {
                    var failed = new CompletableFuture<Void>();
                    failed.completeExceptionally(expected);

                    return failed;
                })
                .build();

        var thrown = awaitFailure(cotani);

        assertEquals("Failed to close resource", thrown.getMessage());
        assertSame(expected, thrown.getCause());
    }

    @Test
    void closeCombinesAsyncAndSyncFailures() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin)
                .withAsync(() -> {
                    throw new RuntimeException("async boom");
                })
                .with(() -> {
                    throw new RuntimeException("sync boom");
                })
                .build();

        var thrown = awaitFailure(cotani);

        assertNotNull(thrown.getCause());
        assertEquals("async boom", thrown.getCause().getMessage());
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("sync boom", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void closeAsyncAfterCloseReturnsCompletedStage() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        cotani.close();

        assertDoesNotThrow(() -> cotani.closeAsync().toCompletableFuture().join());
        assertTrue(closed.get());
    }

    @Test
    void closeCancelsUnfinishedAsyncTeardownOnTimeout() {
        var plugin = pluginWithLogger();
        var teardownStarted = new AtomicBoolean();
        var syncClosed = new AtomicBoolean();
        var gate = new CompletableFuture<Void>();
        var cotani = Cotani.forPlugin(plugin)
                .timeout(Duration.ofMillis(50))
                .withAsync(() -> {
                    teardownStarted.set(true);
                    return gate;
                })
                .with(() -> syncClosed.set(true))
                .build();

        var thrown = awaitFailure(cotani);

        assertTrue(teardownStarted.get());
        assertEquals("Failed to close resource", thrown.getMessage());
        assertInstanceOf(CancellationException.class, thrown.getCause());

        // The timeout does not skip the synchronous teardown phase.
        assertTrue(syncClosed.get());
        // Unfinished async stages are cancelled instead of being abandoned silently.
        assertTrue(gate.isDone());
        assertTrue(gate.isCancelled());

        assertThrows(
                CompletionException.class,
                () -> cotani.closeAsync().toCompletableFuture().join());
    }

    @Test
    void closeStartsTimeoutWithoutBlockingOrInterrupting() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin)
                .timeout(Duration.ofMillis(10))
                .withAsync(CompletableFuture::new)
                .build();

        assertDoesNotThrow(cotani::close);
    }

    @Test
    void closeWithoutInitializedServerDoesNotThrowNullPointer() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        assertDoesNotThrow(cotani::close);

        assertTrue(closed.get());
    }

    @Test
    void closeOnPrimaryThreadStartsNonBlockingTeardown() {
        var plugin = pluginWithLogger();
        var server = Mockito.mock(Server.class);
        Mockito.when(server.isPrimaryThread()).thenReturn(true);
        setBukkitServer(server);
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        assertDoesNotThrow(cotani::close);
        assertTrue(closed.get());
    }

    @Test
    void closeOnNonPrimaryThreadClosesResources() {
        var plugin = pluginWithLogger();
        var server = Mockito.mock(Server.class);
        Mockito.when(server.isPrimaryThread()).thenReturn(false);
        setBukkitServer(server);
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        assertDoesNotThrow(cotani::close);

        assertTrue(closed.get());
    }

    @Test
    void closeAsyncIsAllowedOnPrimaryThread() {
        var plugin = pluginWithLogger();
        var server = Mockito.mock(Server.class);
        Mockito.when(server.isPrimaryThread()).thenReturn(true);
        setBukkitServer(server);
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        assertDoesNotThrow(() -> cotani.closeAsync().toCompletableFuture().join());

        assertTrue(closed.get());
    }
}
