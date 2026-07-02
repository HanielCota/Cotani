package com.cotani.storage.error;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QueryErrorTest {

    @Test
    void storesMessage() {
        var error = new QueryError("test message", null);
        assertEquals("test message", error.message());
    }

    @Test
    void acceptsNullCause() {
        var error = new QueryError("test", null);
        assertNull(error.cause());
    }

    @Test
    void storesCause() {
        var cause = new RuntimeException("root");
        var error = new QueryError("test", cause);
        assertSame(cause, error.cause());
    }

    @Test
    void storageExceptionWrapsQueryError() {
        var error = new QueryError("fail", null);
        var exception = new StorageException(error);
        assertSame(error, exception.error());
        assertEquals("fail", exception.getMessage());
    }

    @Test
    void storageExceptionCapturesCause() {
        var cause = new RuntimeException("root");
        var error = new QueryError("fail", cause);
        var exception = new StorageException(error);
        assertSame(cause, exception.getCause());
    }
}
