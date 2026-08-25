package com.cotani.mail.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.mail.api.MailId;
import com.cotani.mail.api.MailMessage;
import com.cotani.mail.api.MailQuery;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MailStorageIntegrationTest {
    @Test
    void roundTripsAndPaginatesMessagesWithSQLite(@TempDir Path directory) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);

        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("mail.db"))))
                .scheduler(scheduler)
                .migrations(StorageMailRepository.migrations().toArray(Migration[]::new))
                .build();
        try {
            storage.startAsync().toCompletableFuture().join();
            var repository = new StorageMailRepository(storage);
            var sender = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            var recipient = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            var sentAt = Instant.parse("2026-01-01T00:00:00Z");
            var expiresAt = sentAt.plus(Duration.ofDays(1));
            var first = new MailMessage(
                    new MailId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                    sender,
                    recipient,
                    "First",
                    "x".repeat(1_000),
                    sentAt,
                    expiresAt,
                    false);
            var second = new MailMessage(
                    new MailId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                    sender,
                    recipient,
                    "Second",
                    "World",
                    sentAt.plusSeconds(1),
                    expiresAt,
                    false);

            assertEquals(
                    first, repository.saveAsync(first).toCompletableFuture().join());
            assertEquals(
                    second, repository.saveAsync(second).toCompletableFuture().join());
            assertEquals(
                    2,
                    repository
                            .inboxAsync(recipient, MailQuery.firstPage(1), sentAt)
                            .toCompletableFuture()
                            .join()
                            .unreadCount());

            var marked = repository
                    .markReadAsync(recipient, first.id(), sentAt.plusSeconds(2))
                    .toCompletableFuture()
                    .join();
            assertEquals(true, marked.read());
            assertEquals(
                    marked, repository.saveAsync(first).toCompletableFuture().join());
            assertEquals(
                    1,
                    repository
                            .inboxAsync(recipient, MailQuery.firstPage(10).unread(), sentAt)
                            .toCompletableFuture()
                            .join()
                            .messages()
                            .size());
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }
}
