package com.cotani.config.security;

import com.cotani.config.exception.ConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Path-containment checks for configuration files and configured filesystem paths. */
public final class ConfigPaths {
    private ConfigPaths() {}

    public static Path requireContained(Path candidate, Path root) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(root, "root");

        var normalizedRoot = root.toAbsolutePath().normalize();
        var normalizedCandidate = candidate.toAbsolutePath().normalize();

        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new ConfigException("Path escapes base directory: " + normalizedCandidate);
        }

        rejectSymbolicLinks(normalizedCandidate, normalizedRoot);
        if (Files.exists(normalizedCandidate) && Files.exists(normalizedRoot)) {
            try {
                var realRoot = normalizedRoot.toRealPath();
                var realCandidate = normalizedCandidate.toRealPath();

                if (!realCandidate.startsWith(realRoot)) {
                    throw new ConfigException(
                            "Path escapes base directory through a symbolic link: " + normalizedCandidate);
                }
            } catch (IOException failure) {
                throw new ConfigException("Could not verify path containment: " + normalizedCandidate, failure);
            }
        }

        return normalizedCandidate;
    }

    private static void rejectSymbolicLinks(Path candidate, Path root) {
        var current = candidate;

        while (current != null && current.startsWith(root)) {
            if (Files.isSymbolicLink(current)) {
                throw new ConfigException("Symbolic links are not allowed in contained paths: " + current);
            }
            if (current.equals(root)) {
                return;
            }

            current = current.getParent();
        }
    }
}
