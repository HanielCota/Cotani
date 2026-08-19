package com.cotani.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Extra {@link Pagination} scenarios not covered by {@link PaginationTest}: argument validation,
 * clamping edge cases and immutable slices.
 */
final class PaginationAdditionalTest {
    @Test
    void shouldRejectNegativeTotalItems() {
        assertThrows(IllegalArgumentException.class, () -> Pagination.pageCount(-1, 9));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullItems() {
        assertThrows(NullPointerException.class, () -> Pagination.page(null, 0, 9));
    }

    @Test
    void shouldClampToZeroForNonPositivePageCounts() {
        assertEquals(0, Pagination.clampPage(7, 0));
        assertEquals(0, Pagination.clampPage(7, -2));
    }

    @Test
    void shouldReturnWholeListWhenPerPageExceedsSize() {
        var items = List.of("a", "b");

        assertEquals(items, Pagination.page(items, 0, 9));
        assertEquals(items, Pagination.page(items, -1, 9));
    }

    @Test
    void shouldReturnImmutableSlice() {
        var slice = Pagination.page(List.of("a", "b", "c"), 0, 2);

        assertThrows(UnsupportedOperationException.class, () -> slice.add("d"));
    }
}
