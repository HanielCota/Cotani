package com.cotani.storage.error;

public record TransactionError(String message, Throwable cause) implements StorageError {}
