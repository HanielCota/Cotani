package com.cotani.gui.layout;

import java.util.List;
import java.util.Objects;

/**
 * Stateless paging math for slicing collections into fixed-size pages.
 */
public final class Pagination {
    private Pagination() {}

    /**
     * Computes how many pages are needed to display {@code totalItems} items.
     *
     * @param totalItems the item count, zero or more
     * @param perPage the page capacity, must be positive
     * @return the page count; zero when there are no items
     */
    public static int pageCount(int totalItems, int perPage) {
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems cannot be negative: " + totalItems);
        }

        requirePositivePerPage(perPage);

        if (totalItems == 0) {
            return 0;
        }

        return (totalItems + perPage - 1) / perPage;
    }

    /**
     * Clamps a page index into the valid range for the given content size.
     *
     * @param page the requested page index
     * @param pageCount the number of available pages
     * @return a page index between {@code 0} and {@code max(0, pageCount - 1)}
     */
    public static int clampPage(int page, int pageCount) {
        if (pageCount <= 0) {
            return 0;
        }

        return Math.clamp(page, 0, pageCount - 1);
    }

    /**
     * Returns the items visible on the given page. Out-of-range pages are clamped, so the result is
     * never an exception for valid {@code perPage} values.
     *
     * @param items the full item list
     * @param page the requested page index; negative or overflowing indexes are clamped
     * @param perPage the page capacity, must be positive
     * @param <T> the item type
     * @return an immutable slice of {@code items}
     */
    public static <T> List<T> page(List<T> items, int page, int perPage) {
        Objects.requireNonNull(items, "Parameter 'items' must not be null");

        requirePositivePerPage(perPage);

        var clampedPage = clampPage(page, pageCount(items.size(), perPage));
        var from = clampedPage * perPage;
        var to = Math.min(from + perPage, items.size());

        return List.copyOf(items.subList(from, to));
    }

    private static void requirePositivePerPage(int perPage) {
        if (perPage <= 0) {
            throw new IllegalArgumentException("perPage must be positive: " + perPage);
        }
    }
}
