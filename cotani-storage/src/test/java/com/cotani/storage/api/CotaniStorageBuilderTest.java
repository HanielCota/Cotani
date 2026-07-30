package com.cotani.storage.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CotaniStorageBuilderTest {

    @Test
    void positiveSubsecondTimeoutRoundsUpInsteadOfDisablingJdbcTimeout() {
        assertEquals(1, CotaniStorageBuilder.toQueryTimeoutSeconds(Duration.ofNanos(1)));
    }

    @Test
    void timeoutBeyondJdbcIntegerRangeIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CotaniStorageBuilder.toQueryTimeoutSeconds(Duration.ofSeconds((long) Integer.MAX_VALUE + 1)));
    }

    @Test
    void negativeAdmissionQueueCapacityIsRejected() {
        var plugin = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> CotaniStorage.create(plugin).admissionQueueCapacity(-1));
    }
}
