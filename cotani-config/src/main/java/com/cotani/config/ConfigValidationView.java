package com.cotani.config;

import com.cotani.config.validation.ValidationResult;

/** Validates record bindings without requiring mutation or file I/O capabilities. */
public interface ConfigValidationView {
    <T> ValidationResult validate(Class<T> type);

    <T> ValidationResult validate(String path, Class<T> type);
}
