package br.com.cotani.storage.executor;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class StorageExecutor implements AutoCloseable {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService service;

    private StorageExecutor(ExecutorService service) {
        this.service = service;
    }

    public static StorageExecutor fixed(int threads) {
        ThreadFactory factory =
                Thread.ofPlatform().name("cotani-storage-", 0).daemon(true).factory();
        return new StorageExecutor(Executors.newFixedThreadPool(threads, factory));
    }

    public static StorageExecutor virtualThreads() {
        return new StorageExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public Executor executor() {
        return service;
    }

    @Override
    public void close() {
        service.shutdown();
        try {
            if (!service.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException _) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
