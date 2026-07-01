package br.com.cotani.storage.error;

public record QueryError(String message, Throwable cause) implements StorageError {}
