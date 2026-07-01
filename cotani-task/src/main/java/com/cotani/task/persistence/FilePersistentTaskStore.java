package com.cotani.task.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
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

        int estimatedSize = id.length()
                + task.taskName().length()
                + task.scheduledAt().toString().length()
                + task.delay().toString().length()
                + payload.length()
                + 16;

        StringBuilder content = new StringBuilder(estimatedSize);
        content.append(id).append('\n');
        content.append(task.taskName()).append('\n');
        content.append(task.scheduledAt().toString()).append('\n');
        content.append(task.delay().toString()).append('\n');
        content.append(payload);

        try {
            Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save persistent task: " + task.id(), exception);
        }
    }

    @Override
    public List<PersistentTask> loadPending() {
        List<PersistentTask> tasks = new ArrayList<>();

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.toString().endsWith(EXTENSION))
                    .forEach(path -> parse(path).ifPresent(tasks::add));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load persistent tasks", exception);
        }

        return tasks;
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

    private java.util.Optional<PersistentTask> parse(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

            if (lines.size() < 5) {
                return java.util.Optional.empty();
            }

            UUID id = UUID.fromString(lines.get(0));
            String taskName = lines.get(1);
            Instant scheduledAt = Instant.parse(lines.get(2));
            Duration delay = Duration.parse(lines.get(3));
            byte[] payload = Base64.getDecoder().decode(lines.get(4));

            return java.util.Optional.of(new PersistentTask(id, taskName, scheduledAt, delay, payload));
        } catch (IOException | IllegalArgumentException _) {
            return java.util.Optional.empty();
        }
    }
}
