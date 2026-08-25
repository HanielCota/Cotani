package com.cotani.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cotani.inventory.api.CrossServerTransferLock;
import com.cotani.inventory.api.InventoryRepository;
import com.cotani.inventory.api.InventorySyncService;
import com.cotani.inventory.internal.service.DefaultInventorySyncService;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventorySyncValidationTest {

    private InventoryRepository repository;
    private InventorySyncService service;

    @BeforeEach
    void setUp() {
        repository = mock(InventoryRepository.class);
        service = new DefaultInventorySyncService(
                mock(PaperTaskScheduler.class), repository, mock(CrossServerTransferLock.class));
    }

    @Test
    void shouldRejectNonPositiveHistoryLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.historyAsync(UUID.randomUUID(), 0));
        verify(repository, never()).findHistoryAsync(any(), anyInt());
    }

    @Test
    void shouldRejectNonPositiveTransferLockDuration() {
        assertThrows(
                IllegalArgumentException.class, () -> service.beginTransferAsync(UUID.randomUUID(), Duration.ZERO));
    }

    @Test
    void shouldRejectNonPositiveNoopTransferLockDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CrossServerTransferLock.noop().tryLockAsync(UUID.randomUUID(), Duration.ofMillis(-1)));
    }
}
