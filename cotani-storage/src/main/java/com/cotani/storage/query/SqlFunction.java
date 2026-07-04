package com.cotani.storage.query;

@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T value);
}
