package com.cotani.config.exception;

import java.io.Serial;

public class ConfigException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
