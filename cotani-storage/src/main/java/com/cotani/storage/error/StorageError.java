package com.cotani.storage.error;

import org.jspecify.annotations.Nullable;

public sealed interface StorageError
        permits ConnectionError, QueryError, MappingError, MigrationError, TransactionError {
    String message();

    @Nullable
    Throwable cause();
}
