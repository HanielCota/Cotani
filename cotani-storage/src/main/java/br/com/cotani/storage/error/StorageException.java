package br.com.cotani.storage.error;

import java.io.Serial;

public final class StorageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient StorageError error;

    public StorageException(StorageError error) {
        super(error.message(), error.cause());
        this.error = error;
    }

    public StorageError error() {
        return error;
    }
}
