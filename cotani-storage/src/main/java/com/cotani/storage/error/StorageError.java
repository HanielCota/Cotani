package com.cotani.storage.error;

public sealed interface StorageError
        permits ConnectionError, QueryError, MappingError, MigrationError, TransactionError {
    String message();

    Throwable cause();
}
