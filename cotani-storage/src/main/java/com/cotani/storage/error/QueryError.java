package com.cotani.storage.error;

import org.jspecify.annotations.Nullable;

public record QueryError(String message, @Nullable Throwable cause) implements StorageError {}
