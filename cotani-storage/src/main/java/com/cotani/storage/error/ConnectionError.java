package com.cotani.storage.error;

import org.jspecify.annotations.Nullable;

public record ConnectionError(String message, @Nullable Throwable cause) implements StorageError {}
