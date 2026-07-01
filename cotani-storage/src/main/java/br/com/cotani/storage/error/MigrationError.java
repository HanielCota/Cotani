package br.com.cotani.storage.error;

public record MigrationError(String message, Throwable cause) implements StorageError {}
