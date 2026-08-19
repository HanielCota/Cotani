package com.cotani.cache.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CacheExceptionTest {
    @Test
    void cacheExceptionCarriesMessage() {
        var exception = new CacheException("cache broken");

        assertEquals("cache broken", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void cacheExceptionPreservesCause() {
        var cause = new IllegalStateException("root");
        var exception = new CacheException("cache broken", cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    void cacheLoadExceptionIsCacheExceptionWithMessageAndCause() {
        var cause = new IllegalStateException("db down");
        var exception = new CacheLoadException("could not load", cause);

        assertInstanceOf(CacheException.class, exception);
        assertEquals("could not load", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void cacheLoadExceptionWithoutCause() {
        var exception = new CacheLoadException("could not load");

        assertEquals("could not load", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void cacheSaveExceptionIsCacheExceptionWithMessageAndCause() {
        var cause = new IllegalStateException("disk full");
        var exception = new CacheSaveException("could not save", cause);

        assertInstanceOf(CacheException.class, exception);
        assertEquals("could not save", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
