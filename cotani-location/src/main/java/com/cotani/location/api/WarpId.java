package com.cotani.location.api;

import java.util.Objects;

/** Stable key for a global warp. */
public record WarpId(LocationName name) {
    public WarpId {
        Objects.requireNonNull(name, "name");
    }

    public static WarpId of(String name) {
        return new WarpId(LocationName.of(name));
    }
}
