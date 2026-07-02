package com.cotani.storage.error;

public record MappingError(String message, Throwable cause) implements StorageError {}
