package br.com.cotani.storage.backend;

public sealed interface StorageBackend permits MySqlBackend, MariaDbBackend, SQLiteBackend {
    StorageCredentials credentials();
}
