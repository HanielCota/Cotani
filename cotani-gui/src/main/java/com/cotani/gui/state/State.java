package com.cotani.gui.state;

import com.cotani.gui.api.BoolProperty;
import com.cotani.gui.api.Property;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Factory for reactive {@link Property} instances bound to GUI render cycles.
 */
public final class State {

    private State() {}

    /**
     * Creates a property holding the given initial value.
     *
     * @param initial the initial value, must not be {@code null}
     * @param <T> the immutable value type
     * @return a new thread-safe property
     */
    public static <T> Property<T> of(T initial) {
        return new StateProperty<>(initial);
    }

    /**
     * Creates a boolean property with {@link BoolProperty#toggle()} support.
     *
     * @param initial the initial value
     * @return a new thread-safe boolean property
     */
    public static BoolProperty of(boolean initial) {
        return new BoolStateProperty(initial);
    }

    private static final class BoolStateProperty extends StateProperty<Boolean> implements BoolProperty {

        private BoolStateProperty(boolean initial) {
            super(initial);
        }
    }

    static class StateProperty<T> implements Property<T> {

        private final Object lock = new Object();
        private final List<Consumer<? super T>> observers = new CopyOnWriteArrayList<>();

        private T value;

        StateProperty(T initial) {
            this.value = Objects.requireNonNull(initial, "Parameter 'initial' must not be null");
        }

        @Override
        public T get() {
            synchronized (lock) {
                return value;
            }
        }

        @Override
        public void set(T value) {
            Objects.requireNonNull(value, "Parameter 'value' must not be null");

            synchronized (lock) {
                if (Objects.equals(this.value, value)) {
                    return;
                }
                this.value = value;
            }
            notifyObservers(value);
        }

        @Override
        public void update(UnaryOperator<T> mutator) {
            Objects.requireNonNull(mutator, "Parameter 'mutator' must not be null");

            T updated;
            synchronized (lock) {
                updated = Objects.requireNonNull(mutator.apply(value), "The mutator must not return null");
                if (Objects.equals(this.value, updated)) {
                    return;
                }
                this.value = updated;
            }
            notifyObservers(updated);
        }

        @Override
        public Subscription observe(Consumer<? super T> listener) {
            Objects.requireNonNull(listener, "Parameter 'listener' must not be null");

            observers.add(listener);
            return () -> observers.remove(listener);
        }

        private void notifyObservers(T newValue) {
            for (var observer : observers) {
                try {
                    observer.accept(newValue);
                } catch (RuntimeException e) {
                    // Prevent one failing observer from blocking the remaining observers.
                }
            }
        }
    }
}
