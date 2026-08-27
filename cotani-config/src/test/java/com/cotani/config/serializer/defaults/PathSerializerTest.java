package com.cotani.config.serializer.defaults;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.value.ConfigValue;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.Files;
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
        try (var fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            var root = Files.createDirectory(fileSystem.getPath("/config"));
            var outside = Files.createDirectory(fileSystem.getPath("/outside"));
            var link = root.resolve("linked");
            Files.createSymbolicLink(link, outside);
            var symlinkRegistry = new ConfigSerializerRegistry();
            symlinkRegistry.register(PathSerializer.create(root));

            var value = ConfigValue.create("test.yml", "path", "linked/secret.yml", true, symlinkRegistry);
            assertThrows(ConfigException.class, () -> symlinkRegistry.convert(value, Path.class));
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
