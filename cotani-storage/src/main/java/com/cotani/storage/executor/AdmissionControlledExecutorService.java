package com.cotani.storage.executor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Non-blocking admission gate in front of a worker executor.
 *
 * <p>The caller either hands the task to an execution slot, queues it in the bounded admission
 * queue, or receives a {@link RejectedExecutionException}. It never runs storage work itself.
 */
public final class AdmissionControlledExecutorService extends AbstractExecutorService {

    private final ExecutorService workers;
    private final int concurrencyLimit;
    private final int queueCapacity;
    private final ArrayDeque<Runnable> queue;
    private final AtomicLong rejectedOperations = new AtomicLong();
    private final Object lock = new Object();

    private int activeOperations;
    private boolean shutdown;

    public AdmissionControlledExecutorService(ExecutorService workers, int concurrencyLimit, int queueCapacity) {
        this.workers = Objects.requireNonNull(workers, "workers");
        if (concurrencyLimit <= 0) {
            throw new IllegalArgumentException("concurrencyLimit must be positive");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("queueCapacity must not be negative");
        }
        this.concurrencyLimit = concurrencyLimit;
        this.queueCapacity = queueCapacity;
        this.queue = new ArrayDeque<>(Math.min(queueCapacity, 1_024));
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        boolean startImmediately = false;
        synchronized (lock) {
            if (shutdown) {
                reject("Storage executor is shut down.");
            }
            if (activeOperations < concurrencyLimit) {
                activeOperations++;
                startImmediately = true;
            } else if (queue.size() < queueCapacity) {
                queue.addLast(command);
            } else {
                reject("Storage admission queue is full (capacity=" + queueCapacity + ").");
            }
        }
        if (startImmediately) {
            submitToWorker(command);
        }
    }

    public int concurrencyLimit() {
        return concurrencyLimit;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public int activeOperations() {
        synchronized (lock) {
            return activeOperations;
        }
    }

    public int queuedOperations() {
        synchronized (lock) {
            return queue.size();
        }
    }

    public long rejectedOperations() {
        return rejectedOperations.get();
    }

    @Override
    public void shutdown() {
        boolean terminateWorkers;
        synchronized (lock) {
            shutdown = true;
            terminateWorkers = activeOperations == 0 && queue.isEmpty();
            lock.notifyAll();
        }
        if (terminateWorkers) {
            workers.shutdown();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> pending;
        synchronized (lock) {
            shutdown = true;
            pending = new ArrayList<>(queue);
            queue.clear();
            lock.notifyAll();
        }
        pending.addAll(workers.shutdownNow());
        return List.copyOf(pending);
    }

    @Override
    public boolean isShutdown() {
        synchronized (lock) {
            return shutdown;
        }
    }

    @Override
    public boolean isTerminated() {
        synchronized (lock) {
            return shutdown && activeOperations == 0 && queue.isEmpty() && workers.isTerminated();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (lock) {
            while (!(shutdown && activeOperations == 0 && queue.isEmpty())) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(lock, remaining);
            }
        }
        long remaining = Math.max(0L, deadline - System.nanoTime());
        return workers.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    }

    private void submitToWorker(Runnable command) {
        try {
            workers.execute(() -> {
                try {
                    command.run();
                } finally {
                    operationFinished();
                }
            });
        } catch (RuntimeException schedulingFailure) {
            operationFinished();
            throw schedulingFailure;
        }
    }

    private void operationFinished() {
        Runnable next = null;
        boolean terminateWorkers = false;
        synchronized (lock) {
            if (!queue.isEmpty()) {
                next = queue.removeFirst();
            } else {
                activeOperations--;
                terminateWorkers = shutdown && activeOperations == 0;
                lock.notifyAll();
            }
        }
        if (next != null) {
            submitToWorker(next);
        } else if (terminateWorkers) {
            workers.shutdown();
        }
    }

    private void reject(String message) {
        rejectedOperations.incrementAndGet();
        throw new RejectedExecutionException(message);
    }
}
