package com.cotani.gui.api;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Thread-safe reactive state container.
 *
 * <p>Observers registered via {@link #observe(Consumer)} are invoked synchronously on the thread that
 * mutates the value, only when the value actually changes. When a property is bound to an open
 * {@link GuiPanel}, mutations must happen on the thread that owns the viewer (main thread on Paper,
 * the entity region thread on Folia) so re-renders stay thread-safe.
 *
 * @param <T> the immutable value type
 */
public interface Property<T> {
    /**
     * Returns the current value.
     *
     * @return the current value, never {@code null}
     */
    T get();

    /**
     * Replaces the current value and notifies observers when the value changed.
     *
     * @param value the new value, must not be {@code null}
     */
    void set(T value);

    /**
     * Atomically replaces the current value with {@code mutator.apply(get())}.
     *
     * @param mutator the mutation function, must not return {@code null}
     */
    void update(UnaryOperator<T> mutator);

    /**
     * Registers an observer invoked with the new value after every effective change.
     *
     * @param listener the observer, must not be {@code null}
     * @return a subscription that removes the observer when closed
     */
    Subscription observe(Consumer<? super T> listener);

    /**
     * Handle that unregisters an observer once closed. Closing twice is a no-op.
     */
    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
