package com.cotani.storage.backend;

public record MySqlBackend(MySqlCredentials credentials) implements StorageBackend {}
