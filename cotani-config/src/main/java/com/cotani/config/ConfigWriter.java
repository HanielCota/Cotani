package com.cotani.config;

import com.cotani.task.api.TaskChain;

/** Mutates the in-memory configuration and persists it explicitly. */
public interface ConfigWriter {

    void set(String path, Object value);

    void setIfMissing(String path, Object value);

    /** Performs file I/O synchronously and must not be called on a Paper-owned thread. */
    void save();

    /** Persists on the configured explicit asynchronous executor. */
    TaskChain<Void> saveAsync();
}
