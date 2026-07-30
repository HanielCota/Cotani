package com.cotani.config.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.cotani.config.exception.ConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BukkitYamlConfigSourceTest {

    @Test
    void rejectsOversizedFileBeforeYamlParsing(@TempDir Path directory) throws IOException {
        var path = directory.resolve("oversized.yml");
        try (var output = Files.newOutputStream(path)) {
            output.write(new byte[4 * 1024 * 1024 + 1]);
        }

        var source = BukkitYamlConfigSource.create(mock(Plugin.class), "oversized.yml", path, false, false);

        assertThrows(ConfigException.class, source::load);
    }

    @Test
    void rejectsDeeplyNestedYamlBeforeParsing(@TempDir Path directory) throws IOException {
        var path = directory.resolve("deep.yml");
        var yaml = new StringBuilder();
        for (int depth = 0; depth < 70; depth++) {
            yaml.append("  ".repeat(depth)).append("level").append(depth).append(":\n");
        }
        Files.writeString(path, yaml);
        var source = BukkitYamlConfigSource.create(mock(Plugin.class), "deep.yml", path, directory, false, false);

        assertThrows(ConfigException.class, source::load);
    }

    @Test
    void rejectsAliasExpansionBombBeforeParsing(@TempDir Path directory) throws IOException {
        var path = directory.resolve("aliases.yml");
        var yaml = new StringBuilder("base: &base {value: 1}\n");
        for (int alias = 0; alias < 51; alias++) {
            yaml.append("alias").append(alias).append(": *base\n");
        }
        Files.writeString(path, yaml);
        var source = BukkitYamlConfigSource.create(mock(Plugin.class), "aliases.yml", path, directory, false, false);

        assertThrows(ConfigException.class, source::load);
    }

    @Test
    void saveNeverFollowsSymbolicLinkTarget(@TempDir Path directory) throws IOException {
        var root = Files.createDirectory(directory.resolve("root"));
        var outside = directory.resolve("outside.yml");
        Files.writeString(outside, "safe: true\n");
        var link = root.resolve("config.yml");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException unsupported) {
            Assumptions.abort("symbolic links are unavailable: " + unsupported.getMessage());
        }
        var source = BukkitYamlConfigSource.create(mock(Plugin.class), "config.yml", link, root, false, false);
        source.set("safe", false);

        assertThrows(ConfigException.class, source::save);
        assertEquals("safe: true\n", Files.readString(outside));
    }
}
