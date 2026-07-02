package com.cotani.config.binder;

import com.cotani.config.section.ConfigSection;
import com.cotani.config.validation.ValidationResult;

public interface ConfigBinder {

    <T> T bind(ConfigSection section, Class<T> type);

    <T> ValidationResult validate(ConfigSection section, Class<T> type);
}
