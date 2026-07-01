package com.cotani.task.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TaskMetadata(String name, ExecutionTarget target, Instant createdAt) {

    private static final int DEFAULT_CACHE_MAX_SIZE = 256;
    private static final Map<String, TaskMetadata> DEFAULT_CACHE = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TaskMetadata> eldest) {
            return size() > DEFAULT_CACHE_MAX_SIZE;
        }
    });

    public TaskMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static TaskMetadata unnamed(ExecutionTarget target) {
        Objects.requireNonNull(target, "target");

        return new TaskMetadata("unnamed-task", target, Instant.now());
    }

    public static TaskMetadata named(String name, ExecutionTarget target) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(target, "target");

        return new TaskMetadata(name, target, Instant.now());
    }

    public static TaskMetadata defaultFor(String name) {
        Objects.requireNonNull(name, "name");

        return DEFAULT_CACHE.computeIfAbsent(
                name, key -> new TaskMetadata(key, ExecutionTarget.async(), Instant.now()));
    }
}
