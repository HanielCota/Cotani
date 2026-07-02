package com.cotani.config.impl;

import java.util.Collection;
import java.util.List;

final class ListCopy {

    private ListCopy() {}

    static <T> List<T> copy(Collection<T> collection) {
        return List.copyOf(collection);
    }
}
