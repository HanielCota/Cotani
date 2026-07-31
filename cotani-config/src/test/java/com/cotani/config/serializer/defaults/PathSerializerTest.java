package com.cotani.config.serializer.defaults;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.value.ConfigValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
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
        registry.register(PathSerializer.create(tempFolder));
    }

    @Test
    void resolvesRelativePath() {
        var value = ConfigValue.create("test.yml", "path", "sub/file.txt", true, registry);
        var result = registry.convert(value, Path.class);
        assertEquals(tempFolder.resolve("sub/file.txt").normalize(), result);
    }

    @Test
    void resolvesAbsolutePathWithinBase() {
        var value = ConfigValue.create(
                "test.yml", "path", tempFolder.resolve("file.txt").toString(), true, registry);
        var result = registry.convert(value, Path.class);
        assertEquals(tempFolder.resolve("file.txt").normalize(), result);
    }

    @Test
    void rejectsPathEscape() {
        var value = ConfigValue.create("test.yml", "path", "../outside.txt", true, registry);
        assertThrows(ConfigException.class, () -> registry.convert(value, Path.class));
    }

    @Test
    void rejectsSymlinkEscape() throws Exception {
        var outside = tempFolder.resolveSibling(tempFolder.getFileName() + "-outside");
        Files.createDirectories(outside);
        var link = tempFolder.resolve("linked");

        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.abort("Symbolic links unavailable: " + unavailable.getMessage());
        }

        try {
            var value = ConfigValue.create("test.yml", "path", "linked/secret.yml", true, registry);
            assertThrows(ConfigException.class, () -> registry.convert(value, Path.class));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsInvalidPathCharactersUtf16() {
        var value = ConfigValue.create("test.yml", "path", "\0", true, registry);
        var ex = assertThrows(ConfigException.class, () -> registry.convert(value, Path.class));
        assertTrue(ex.getMessage().contains("Invalid path"));
    }
}
