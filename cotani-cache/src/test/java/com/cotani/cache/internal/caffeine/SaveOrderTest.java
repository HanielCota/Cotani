package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class SaveOrderTest {
    @Test
    void generationTakesPrecedenceOverVersion() {
        SaveOrder olderGenerationNewerVersion = new SaveOrder(1, 99);
        SaveOrder newerGenerationOlderVersion = new SaveOrder(2, 1);

        assertTrue(olderGenerationNewerVersion.compareTo(newerGenerationOlderVersion) < 0);
        assertTrue(newerGenerationOlderVersion.compareTo(olderGenerationNewerVersion) > 0);
    }

    @Test
    void versionBreaksTiesWithinSameGeneration() {
        SaveOrder first = new SaveOrder(1, 1);
        SaveOrder second = new SaveOrder(1, 2);

        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
        assertEquals(0, first.compareTo(new SaveOrder(1, 1)));
    }

    @Test
    void noneIsOlderThanAnyRealOrder() {
        SaveOrder earliest = new SaveOrder(Long.MIN_VALUE + 1, Long.MIN_VALUE + 1);

        assertTrue(SaveOrder.NONE.compareTo(earliest) < 0);
        assertTrue(earliest.compareTo(SaveOrder.NONE) > 0);
    }

    @Test
    void sortedListOrdersByGenerationThenVersion() {
        List<SaveOrder> orders = List.of(new SaveOrder(2, 1), new SaveOrder(1, 2), new SaveOrder(1, 1), SaveOrder.NONE);

        var sorted = orders.stream().sorted().toList();

        assertEquals(List.of(SaveOrder.NONE, new SaveOrder(1, 1), new SaveOrder(1, 2), new SaveOrder(2, 1)), sorted);
    }
}
