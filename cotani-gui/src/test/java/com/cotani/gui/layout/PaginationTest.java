package com.cotani.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class PaginationTest {

    @Test
    void computesPageCount() {
        assertEquals(0, Pagination.pageCount(0, 9));
        assertEquals(1, Pagination.pageCount(9, 9));
        assertEquals(2, Pagination.pageCount(10, 9));
        assertEquals(2, Pagination.pageCount(18, 9));
        assertEquals(3, Pagination.pageCount(19, 9));
    }

    @Test
    void slicesFullAndPartialPages() {
        var items = IntStream.range(0, 19).boxed().toList();

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), Pagination.page(items, 0, 9));
        assertEquals(List.of(9, 10, 11, 12, 13, 14, 15, 16, 17), Pagination.page(items, 1, 9));
        assertEquals(List.of(18), Pagination.page(items, 2, 9));
    }

    @Test
    void clampsOutOfRangePages() {
        var items = IntStream.range(0, 19).boxed().toList();

        assertEquals(Pagination.page(items, 2, 9), Pagination.page(items, 99, 9));
        assertEquals(Pagination.page(items, 0, 9), Pagination.page(items, -3, 9));
    }

    @Test
    void returnsEmptySliceForEmptyContent() {
        assertTrue(Pagination.page(List.of(), 0, 9).isEmpty());
        assertTrue(Pagination.page(List.of(), 5, 9).isEmpty());
    }

    @Test
    void rejectsNonPositivePageCapacity() {
        assertThrows(IllegalArgumentException.class, () -> Pagination.page(List.of(1), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Pagination.pageCount(10, -1));
    }
}
