package com.cotani.storage.backend;

public record SQLiteBackend(SQLiteCredentials credentials) implements StorageBackend {}
