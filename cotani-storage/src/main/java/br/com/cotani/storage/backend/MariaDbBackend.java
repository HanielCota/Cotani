package br.com.cotani.storage.backend;

public record MariaDbBackend(MariaDbCredentials credentials) implements StorageBackend {}
