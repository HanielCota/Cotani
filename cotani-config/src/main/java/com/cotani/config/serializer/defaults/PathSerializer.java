package com.cotani.config.serializer.defaults;

import com.cotani.config.security.ConfigPaths;
import com.cotani.config.serializer.ConfigSerializer;
import com.cotani.config.value.ConfigValue;
import java.nio.file.Path;
import java.util.Objects;

public final class PathSerializer implements ConfigSerializer<Path> {

    private final Path baseFolder;

    public PathSerializer(Path baseFolder) {
        this.baseFolder = Objects.requireNonNull(baseFolder, "baseFolder")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public Class<Path> type() {
        return Path.class;
    }

    @Override
    public Path read(ConfigValue value) {
        try {
            return ConfigPaths.requireContained(baseFolder.resolve(value.asString()), baseFolder);
        } catch (java.nio.file.InvalidPathException exception) {
            throw new com.cotani.config.exception.ConfigException(
                    "Invalid path at " + value.location() + ": " + exception.getMessage(), exception);
        }
    }

    @Override
    public Object write(Path value) {
        return value.toString();
    }
}
