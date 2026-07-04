package com.cotani.task.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePersistentTaskStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadTask() {
        FilePersistentTaskStore store = new FilePersistentTaskStore(tempDir);
        UUID id = UUID.randomUUID();
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);

        PersistentTask task = new PersistentTask(id, "backup", Instant.now(), Duration.ofMinutes(5), payload);
        store.save(task);

        List<PersistentTask> pending = store.loadPending();

        assertEquals(1, pending.size());
        assertEquals(id, pending.getFirst().id());
        assertEquals("backup", pending.getFirst().taskName());
        assertArrayEquals(payload, pending.getFirst().payload());
    }

    @Test
    void markCompletedRemovesTask() {
        FilePersistentTaskStore store = new FilePersistentTaskStore(tempDir);
        PersistentTask task =
                new PersistentTask(UUID.randomUUID(), "cleanup", Instant.now(), Duration.ofSeconds(10), new byte[0]);

        store.save(task);
        store.markCompleted(task);

        assertTrue(store.loadPending().isEmpty());
        assertTrue(Files.notExists(tempDir.resolve(task.id() + ".task")));
    }
}
