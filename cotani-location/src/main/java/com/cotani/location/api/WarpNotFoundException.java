package com.cotani.location.api;

import java.util.Objects;

/** Raised when a requested warp does not exist. */
public final class WarpNotFoundException extends LocationException {
    private static final long serialVersionUID = 1L;
    private final transient WarpId id;

    public WarpNotFoundException(WarpId id) {
        super("Warp not found: " + Objects.requireNonNull(id, "id"));
        this.id = id;
    }

    public WarpId id() {
        return id;
    }
}
