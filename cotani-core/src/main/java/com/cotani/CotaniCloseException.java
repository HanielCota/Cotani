package com.cotani;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CotaniCloseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CotaniCloseException(String message, Throwable cause) {
        super(message, cause);
    }
}
