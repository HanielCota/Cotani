package com.cotani.storage.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathsTest {
    @TempDir
    Path tempDir;

    @Test
    void requireContainedAcceptsNormalPath() {
        var resolved = tempDir.resolve("sub/file.db");
        assertEquals(resolved.normalize(), Paths.requireContained(resolved, tempDir));
    }

    @Test
    void requireContainedRejectsEscapingPath() {
        var outside = tempDir.resolve("../outside.db");
        assertThrows(IllegalArgumentException.class, () -> Paths.requireContained(outside, tempDir));
    }

    @Test
    void requireContainedRejectsAbsoluteEscape() {
        var outside = Path.of("C:\\Windows\\system32\\evil.db");
        assertThrows(IllegalArgumentException.class, () -> Paths.requireContained(outside, tempDir));
    }

    @Test
    void requireContainedAcceptsExactRoot() {
        assertEquals(tempDir.normalize(), Paths.requireContained(tempDir, tempDir));
    }

    @Test
    void requireContainedRejectsSymbolicLinkEvenWhenTargetIsInsideRoot() throws IOException {
        try (var fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            var root = Files.createDirectory(fileSystem.getPath("/storage"));
            var target = Files.createFile(root.resolve("target.db"));
            var link = root.resolve("link.db");
            Files.createSymbolicLink(link, target);

            assertThrows(IllegalArgumentException.class, () -> Paths.requireContained(link, root));
        }
    }
}
