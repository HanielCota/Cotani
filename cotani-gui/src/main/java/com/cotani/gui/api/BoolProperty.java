package com.cotani.gui.api;

/**
 * A {@link Property} specialized for {@link Boolean} values with a convenience {@link #toggle()}.
 */
public interface BoolProperty extends Property<Boolean> {
    /**
     * Flips the current value and notifies observers.
     */
    default void toggle() {
        update(on -> !on);
    }
}
