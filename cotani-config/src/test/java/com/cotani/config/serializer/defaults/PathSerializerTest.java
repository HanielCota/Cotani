package com.cotani.config.serializer.defaults;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.value.ConfigValue;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathSerializerTest {

    private ConfigSerializerRegistry registry;
    private Path tempFolder;

    @BeforeEach
    void setUp(@TempDir Path tempFolder) {
        this.tempFolder = tempFolder;
        registry = new ConfigSerializerRegistry();
        registry.register(new PathSerializer(tempFolder));
    }

    @Test
    void resolvesRelativePath() {
        var value = new ConfigValue("test.yml", "path", "sub/file.txt", true, registry);
        var result = registry.convert(value, Path.class);
        assertEquals(tempFolder.resolve("sub/file.txt").normalize(), result);
    }

    @Test
    void resolvesAbsolutePathWithinBase() {
        var value = new ConfigValue(
                "test.yml", "path", tempFolder.resolve("file.txt").toString(), true, registry);
        var result = registry.convert(value, Path.class);
        assertEquals(tempFolder.resolve("file.txt").normalize(), result);
    }

    @Test
    void rejectsPathEscape() {
        var value = new ConfigValue("test.yml", "path", "../outside.txt", true, registry);
        assertThrows(ConfigException.class, () -> registry.convert(value, Path.class));
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsInvalidPathCharactersUtf16() {
        var value = new ConfigValue("test.yml", "path", "\0", true, registry);
        var ex = assertThrows(ConfigException.class, () -> registry.convert(value, Path.class));
        assertTrue(ex.getMessage().contains("Invalid path"));
    }
}
