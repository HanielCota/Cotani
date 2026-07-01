package br.com.cotani.storage.security;

import java.nio.file.Path;

public final class Paths {

    private Paths() {}

    public static Path requireContained(Path path, Path root) {
        Path normalized = path.toAbsolutePath().normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path must be inside " + normalizedRoot + ": " + normalized);
        }
        return normalized;
    }
}
