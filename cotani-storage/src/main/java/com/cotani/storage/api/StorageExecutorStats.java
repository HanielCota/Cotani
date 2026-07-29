package com.cotani.storage.api;

/** Immutable snapshot of storage admission and execution pressure. */
public record StorageExecutorStats(
        int concurrencyLimit, int queueCapacity, int activeOperations, int queuedOperations, long rejectedOperations) {}
