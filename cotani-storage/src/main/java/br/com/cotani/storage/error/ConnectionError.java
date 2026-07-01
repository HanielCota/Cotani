package br.com.cotani.storage.error;

public record ConnectionError(String message, Throwable cause) implements StorageError {}
