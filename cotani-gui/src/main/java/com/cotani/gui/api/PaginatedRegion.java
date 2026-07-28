package com.cotani.gui.api;

import com.cotani.gui.button.Button;
import com.cotani.gui.layout.Pagination;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Package-private paginated region: slices a collection into the slots of a structure symbol and
 * keeps the visible page in sync with the page property.
 */
final class PaginatedRegion<T> {

    private final List<Integer> slots;
    private final Property<Integer> page;
    private final List<T> items;
    private final Function<T, Button> renderer;

    PaginatedRegion(List<Integer> slots, Property<Integer> page, List<T> items, Function<T, Button> renderer) {
        this.slots = slots;
        this.page = page;
        this.items = items;
        this.renderer = renderer;
    }

    Property<Integer> page() {
        return page;
    }

    void renderInto(GuiPanel panel) {
        var perPage = slots.size();
        if (perPage == 0) {
            return;
        }

        var currentPage = page.get();
        var clampedPage = Pagination.clampPage(currentPage, Pagination.pageCount(items.size(), perPage));
        if (!Objects.equals(clampedPage, currentPage)) {
            // Notifies observers (this region re-renders once more with the clamped value).
            page.set(clampedPage);
        }

        var slice = Pagination.page(items, clampedPage, perPage);
        for (var index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (index < slice.size()) {
                panel.setDynamicSlot(slot, renderer.apply(slice.get(index)));
                continue;
            }

            panel.clearDynamicSlot(slot);
        }
    }
}
