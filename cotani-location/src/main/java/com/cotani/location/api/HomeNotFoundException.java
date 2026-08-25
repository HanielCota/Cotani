package com.cotani.location.api;

import java.util.Objects;

/** Raised when a requested home does not exist. */
public final class HomeNotFoundException extends LocationException {
    private static final long serialVersionUID = 1L;
    private final transient HomeId id;

    public HomeNotFoundException(HomeId id) {
        super("Home not found: " + Objects.requireNonNull(id, "id"));
        this.id = id;
    }

    public HomeId id() {
        return id;
    }
}
