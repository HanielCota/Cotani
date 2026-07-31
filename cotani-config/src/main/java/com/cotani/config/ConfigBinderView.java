package com.cotani.config;

/** Binds an immutable configuration snapshot to record-based domain types. */
public interface ConfigBinderView {
    <T> T bind(Class<T> type);

    <T> T bind(String path, Class<T> type);

    <T> T bindOrThrow(Class<T> type);

    <T> T bindOrThrow(String path, Class<T> type);
}
