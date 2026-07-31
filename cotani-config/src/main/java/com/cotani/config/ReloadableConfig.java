package com.cotani.config;

import com.cotani.task.api.TaskChain;

/** Reloads a configuration snapshot from its source. */
public interface ReloadableConfig {
    /** Performs file I/O synchronously and must not be called on a Paper-owned thread. */
    void reload();

    /** Reloads on the configured explicit asynchronous executor. */
    TaskChain<Void> reloadAsync();
}
