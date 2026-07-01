package com.cotani.task.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class FilePersistentTaskStore implements PersistentTaskStore {

    private static final String EXTENSION = ".task";

    private final Path directory;

    public FilePersistentTaskStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");

        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create task persistence directory: " + directory, exception);
        }
    }

    @Override
    public void save(PersistentTask task) {
        Objects.requireNonNull(task, "task");

        Path file = directory.resolve(task.id() + EXTENSION);
        String id = task.id().toString();
        String payload = Base64.getEncoder().encodeToString(task.payload());
        String content = String.join(
                "\n",
                id,
                task.taskName(),
                task.scheduledAt().toString(),
                task.delay().toString(),
                payload);

        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save persistent task: " + task.id(), exception);
        }
    }

    @Override
    public List<PersistentTask> loadPending() {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(EXTENSION))
                    .map(this::parse)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load persistent tasks", exception);
        }
    }

    @Override
    public void markCompleted(PersistentTask task) {
        Objects.requireNonNull(task, "task");

        Path file = directory.resolve(task.id() + EXTENSION);

        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete completed task: " + task.id(), exception);
        }
    }

    private Optional<PersistentTask> parse(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

            if (lines.size() < 5) {
                return Optional.empty();
            }

            UUID id = UUID.fromString(lines.get(0));
            String taskName = lines.get(1);
            Instant scheduledAt = Instant.parse(lines.get(2));
            Duration delay = Duration.parse(lines.get(3));
            byte[] payload = Base64.getDecoder().decode(lines.get(4));

            return Optional.of(new PersistentTask(id, taskName, scheduledAt, delay, payload));
        } catch (IOException | IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
