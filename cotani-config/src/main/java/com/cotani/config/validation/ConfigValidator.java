package com.cotani.config.validation;

import com.cotani.config.annotation.Range;
import com.cotani.config.annotation.Required;
import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.value.ConfigValue;
import java.lang.reflect.RecordComponent;
import java.util.Objects;

public final class ConfigValidator {

    private final ConfigSerializerRegistry serializers;

    public ConfigValidator(ConfigSerializerRegistry serializers) {
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    public ValidationResult validateComponent(ConfigValue value, RecordComponent component) {
        var result = ValidationResult.valid();
        Required required = component.getAnnotation(Required.class);
        Range range = component.getAnnotation(Range.class);
        if (required != null) {
            result.merge(validateRequired(value));
        }
        if (range != null) {
            result.merge(validateRange(value, component, range));
        }
        return result;
    }

    private ValidationResult validateRequired(ConfigValue value) {
        var result = ValidationResult.valid();
        if (value.exists() && value.raw() != null) {
            return result;
        }
        result.add(new ConfigIssue(value.file(), value.path(), "required value is missing"));
        return result;
    }

    private ValidationResult validateRange(ConfigValue value, RecordComponent component, Range range) {
        var result = ValidationResult.valid();
        if (range.min() > range.max()) {
            result.add(new ConfigIssue(
                    value.file(),
                    value.path(),
                    "invalid range annotation: min " + range.min() + " is greater than max " + range.max()));
            return result;
        }
        if (!value.exists() || value.raw() == null) {
            return result;
        }
        Object converted;
        try {
            converted = serializers.convert(value, component.getType());
        } catch (ConfigException exception) {
            result.add(new ConfigIssue(
                    value.file(), value.path(), Objects.toString(exception.getMessage(), "invalid value")));
            return result;
        }
        if (!(converted instanceof Number number)) {
            result.add(new ConfigIssue(
                    value.file(),
                    value.path(),
                    "@Range can only be used with numeric components but " + component.getName() + " is "
                            + component.getType().getName()));
            return result;
        }
        var current = number.doubleValue();
        if (current < range.min()) {
            result.add(new ConfigIssue(
                    value.file(), value.path(), "value must be greater than or equal to " + range.min()));
        }
        if (current > range.max()) {
            result.add(
                    new ConfigIssue(value.file(), value.path(), "value must be lower than or equal to " + range.max()));
        }
        return result;
    }
}
