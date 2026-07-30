package com.cotani.config;

import com.cotani.config.section.ConfigSection;
import com.cotani.config.value.ConfigValue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;

/** Read-only view of a loaded configuration snapshot. */
public interface ConfigReader {

    String name();

    Path path();

    boolean contains(String path);

    ConfigValue value(String path);

    ConfigSection section(String path);

    Optional<String> optionalString(String path);

    String getString(String path, String fallback);

    int getInt(String path, int fallback);

    long getLong(String path, long fallback);

    double getDouble(String path, double fallback);

    boolean getBoolean(String path, boolean fallback);

    Duration getDuration(String path, Duration fallback);

    Component getComponent(String path, Component fallback);

    <T> T get(String path, Class<T> type, T fallback);

    <T> List<T> getList(String path, Class<T> type);
}
