package com.cotani.task.executor;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.impl.executor.VirtualThreadExecutor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VirtualThreadExecutorTest {

    private static final TaskMetadata METADATA = TaskMetadata.named("test-task", ExecutionTarget.async());

    @Test
    void executesAndShutsDown() throws ExecutionException, InterruptedException, TimeoutException {
        VirtualThreadExecutor executor = new VirtualThreadExecutor();
        AtomicBoolean executed = new AtomicBoolean(false);

        var future = executor.submit(METADATA, () -> executed.set(true));

        future.get(5, TimeUnit.SECONDS);
        assertTrue(executed.get());

        executor.close();
    }

    @Test
    void closeShutsDownExecutor() {
        VirtualThreadExecutor executor = new VirtualThreadExecutor();

        executor.close();

        assertTrue(executor.isShutdown());
    }

    @Test
    void rejectsAfterShutdown() {
        VirtualThreadExecutor executor = new VirtualThreadExecutor();
        executor.close();

        assertThrows(RejectedExecutionException.class, () -> executor.submit(METADATA, () -> {}));
    }

    @Test
    void namesThreadWithTaskName() throws ExecutionException, InterruptedException, TimeoutException {
        VirtualThreadExecutor executor = new VirtualThreadExecutor();
        AtomicReference<String> capturedName = new AtomicReference<>();
        TaskMetadata metadata = TaskMetadata.named("custom-task-name", ExecutionTarget.async());

        var future = executor.submit(
                metadata, () -> capturedName.set(Thread.currentThread().getName()));

        future.get(5, TimeUnit.SECONDS);
        String name = capturedName.get();
        assertNotNull(name);
        assertTrue(name.contains("custom-task-name"));

        executor.close();
    }

    @Test
    void restoresThreadNameAfterExecution() throws ExecutionException, InterruptedException, TimeoutException {
        VirtualThreadExecutor executor = new VirtualThreadExecutor();
        AtomicReference<String> capturedName = new AtomicReference<>();
        String originalName = Thread.currentThread().getName();

        var future = executor.submit(METADATA, () -> {
            Thread.currentThread().setName("should-be-restored");
            capturedName.set(Thread.currentThread().getName());
        });

        future.get(5, TimeUnit.SECONDS);
        String name = capturedName.get();
        assertNotNull(name);
        assertTrue(name.contains("should-be-restored"));
        assertTrue(Thread.currentThread().getName().contains(originalName));

        executor.close();
    }
}
