package com.cotani.cache.api;

import java.util.UUID;

@FunctionalInterface
public interface PlayerValueFactory<V> {

    V create(UUID uniqueId);
}
