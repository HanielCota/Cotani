package com.cotani;

import java.io.Serial;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CotaniCloseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CotaniCloseException(String message, Throwable cause) {
        super(message, cause);
    }
}
