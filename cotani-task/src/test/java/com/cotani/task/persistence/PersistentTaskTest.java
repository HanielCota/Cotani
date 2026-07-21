package com.cotani.task.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistentTaskTest {

    @Test
    void payloadIsClonedOnConstruction() {
        byte[] original = {1, 2, 3};
        PersistentTask task =
                new PersistentTask(UUID.randomUUID(), "test", Instant.now(), Duration.ofMinutes(1), original);

        original[0] = 99;

        assertEquals(1, task.payload()[0]);
    }

    @Test
    void payloadAccessorReturnsDefensiveCopy() {
        byte[] original = {1, 2, 3};
        PersistentTask task =
                new PersistentTask(UUID.randomUUID(), "test", Instant.now(), Duration.ofMinutes(1), original);

        task.payload()[0] = 99;

        assertEquals(1, task.payload()[0]);
    }

    @Test
    void repeatedPayloadAccessReturnIndependentCopies() {
        byte[] original = {1, 2, 3};
        PersistentTask task =
                new PersistentTask(UUID.randomUUID(), "test", Instant.now(), Duration.ofMinutes(1), original);

        byte[] first = task.payload();
        byte[] second = task.payload();
        first[0] = 99;

        assertEquals(1, second[0]);
    }
}
