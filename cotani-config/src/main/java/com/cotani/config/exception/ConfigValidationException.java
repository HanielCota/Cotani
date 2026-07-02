package com.cotani.config.exception;

import com.cotani.config.validation.ValidationResult;
import java.io.Serial;

public final class ConfigValidationException extends ConfigException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ValidationResult result;

    public ConfigValidationException(ValidationResult result) {
        super(result.format());
        this.result = result;
    }

    public ValidationResult result() {
        return result;
    }
}
