package com.cotani.storage.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Paths {
    private Paths() {}

    public static Path requireContained(Path path, Path root) {
        var normalized = path.toAbsolutePath().normalize();
        var normalizedRoot = root.toAbsolutePath().normalize();

        if (!startsWith(normalized, normalizedRoot)) {
            throw new IllegalArgumentException("Path must be inside " + normalizedRoot + ": " + normalized);
        }

        requireNoSymbolicLinks(normalized);

        return normalized;
    }

    public static Path requireNoSymbolicLinks(Path path) {
        var normalized = path.toAbsolutePath().normalize();
        var current = normalized;

        while (current != null) {
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Symbolic links are not allowed in storage paths: " + current);
            }

            current = current.getParent();
        }

        return normalized;
    }

    private static boolean startsWith(Path candidate, Path root) {
        if (!candidate.startsWith(root)) {
            return false;
        }
        // Resolve symlinks when the target exists to prevent TOCTOU escape.
        if (Files.exists(candidate)) {
            try {
                var real = candidate.toRealPath();
                var realRoot = root.toRealPath();

                return real.startsWith(realRoot);
            } catch (IOException failure) {
                throw new IllegalArgumentException("Could not verify path containment: " + candidate, failure);
            }
        }
        // For non-existent paths, walk up and reject symlinked parents.
        return !hasSymlinkedParent(candidate, root);
    }

    private static boolean hasSymlinkedParent(Path candidate, Path root) {
        var current = candidate.getParent();

        while (current != null && current.startsWith(root)) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
            if (current.equals(root)) {
                return false;
            }

            current = current.getParent();
        }

        return false;
    }
}
