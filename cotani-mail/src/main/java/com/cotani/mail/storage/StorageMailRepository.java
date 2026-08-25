package com.cotani.mail.storage;

import com.cotani.mail.api.MailConflictException;
import com.cotani.mail.api.MailId;
import com.cotani.mail.api.MailMessage;
import com.cotani.mail.api.MailNotFoundException;
import com.cotani.mail.api.MailPage;
import com.cotani.mail.api.MailQuery;
import com.cotani.mail.api.MailRepository;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/** SQL-backed repository with idempotent message writes and indexed inbox queries. */
public final class StorageMailRepository implements MailRepository {
    private static final String TABLE = "cotani_mail_messages";
    private final CotaniStorage storage;

    public StorageMailRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<MailMessage> saveAsync(MailMessage message) {
        Objects.requireNonNull(message, "message");
        return insert(message).handle((ignored, failure) -> failure).thenCompose(failure -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(message);
            }
            if (!isUniqueViolation(failure)) {
                return CompletableFuture.failedFuture(failure);
            }
            return findAsync(message.id()).thenCompose(existing -> {
                if (existing.isEmpty()) {
                    return CompletableFuture.failedFuture(failure);
                }
                var previous = existing.orElseThrow();
                if (!previous.hasSameContentAs(message)) {
                    return CompletableFuture.failedFuture(new MailConflictException(message.id()));
                }
                return CompletableFuture.completedFuture(previous);
            });
        });
    }

    private static boolean isUniqueViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CompletionException || current instanceof ExecutionException) {
                if (current.getCause() != null) {
                    current = current.getCause();
                    continue;
                }
            }
            if (current instanceof SQLException sqlException) {
                var state = sqlException.getSQLState();
                if (state != null && state.startsWith("23")) {
                    return true;
                }
            }
            var message = current.getMessage();
            if (message != null) {
                var normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("unique constraint") || normalized.contains("duplicate entry")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    public CompletionStage<MailPage> inboxAsync(UUID recipientId, MailQuery query, Instant now) {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(now, "now");
        if (query.pageSize() == Integer.MAX_VALUE) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("pageSize is too large"));
        }
        var pageSql = "SELECT message_id, sender_id, recipient_id, subject, body, sent_at, expires_at, is_read FROM "
                + TABLE
                + " WHERE recipient_id = ? AND expires_at > ?"
                + (query.unreadOnly() ? " AND is_read = ?" : "")
                + " ORDER BY sent_at DESC, message_id DESC LIMIT ? OFFSET ?";
        var pageStage = storage.queryExecutor()
                .queryMany(
                        pageSql,
                        binder -> {
                            binder.set(recipientId);
                            binder.set(now);
                            if (query.unreadOnly()) {
                                binder.set(false);
                            }
                            binder.set(query.pageSize() + 1);
                            binder.set((long) query.page() * query.pageSize());
                        },
                        StorageMailRepository::mapRow);
        var unreadStage = storage.queryExecutor()
                .queryOne(
                        "SELECT COUNT(*) AS unread_count FROM " + TABLE
                                + " WHERE recipient_id = ? AND expires_at > ? AND is_read = ?",
                        binder -> {
                            binder.set(recipientId);
                            binder.set(now);
                            binder.set(false);
                        },
                        row -> row.getLong("unread_count"));
        return pageStage.thenCombine(unreadStage, (rows, unreadCount) -> {
            var hasMore = rows.size() > query.pageSize();
            var messages = new ArrayList<>(rows.subList(0, Math.min(rows.size(), query.pageSize())));
            return new MailPage(messages, hasMore, Math.toIntExact(unreadCount.orElseThrow()));
        });
    }

    @Override
    public CompletionStage<MailMessage> markReadAsync(UUID recipientId, MailId id, Instant readAt) {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(readAt, "readAt");
        return findAsync(id).thenCompose(existing -> {
            var message = existing.filter(value -> value.recipientId().equals(recipientId))
                    .orElseThrow(() -> new MailNotFoundException(recipientId, id));
            if (message.isExpired(readAt)) {
                return CompletableFuture.failedFuture(new MailNotFoundException(recipientId, id));
            }
            return storage.table(TABLE)
                    .update()
                    .set("is_read", true)
                    .where("message_id", messageId(id))
                    .where("recipient_id", recipientId)
                    .execute()
                    .thenApply(ignored -> message.markRead());
        });
    }

    @Override
    public CompletionStage<Void> deleteAsync(UUID recipientId, MailId id) {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(id, "id");
        return findAsync(id).thenCompose(existing -> {
            if (existing.isEmpty() || !existing.orElseThrow().recipientId().equals(recipientId)) {
                return CompletableFuture.failedFuture(new MailNotFoundException(recipientId, id));
            }
            return storage.table(TABLE)
                    .delete()
                    .where("message_id", messageId(id))
                    .execute();
        });
    }

    @Override
    public CompletionStage<Void> purgeExpiredAsync(Instant now) {
        Objects.requireNonNull(now, "now");
        return storage.queryExecutor()
                .update("DELETE FROM " + TABLE + " WHERE expires_at <= ?", binder -> binder.set(now));
    }

    public static List<Migration> migrations() {
        return List.of(
                new CreateMailTablesMigration(), new CreateMailIndexesMigration(), new MigrateMailBodyTextMigration());
    }

    private CompletionStage<Void> insert(MailMessage message) {
        return storage.queryExecutor()
                .update(
                        "INSERT INTO " + TABLE
                                + " (message_id, sender_id, recipient_id, subject, body, sent_at, expires_at, is_read) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        binder -> {
                            binder.set(messageId(message.id()));
                            binder.set(message.senderId());
                            binder.set(message.recipientId());
                            binder.set(message.subject());
                            binder.set(message.body());
                            binder.set(message.sentAt());
                            binder.set(message.expiresAt());
                            binder.set(message.read());
                        });
    }

    private CompletionStage<java.util.Optional<MailMessage>> findAsync(MailId id) {
        return storage.table(TABLE).select().where("message_id", messageId(id)).one(StorageMailRepository::mapRow);
    }

    private static MailMessage mapRow(com.cotani.storage.query.Row row) throws SQLException {
        return new MailMessage(
                new MailId(UUID.fromString(row.getString("message_id"))),
                row.getUuidOptional("sender_id").orElseThrow(),
                row.getUuidOptional("recipient_id").orElseThrow(),
                row.getString("subject"),
                row.getString("body"),
                row.getInstantOptional("sent_at").orElseThrow(),
                row.getInstantOptional("expires_at").orElseThrow(),
                row.getBoolean("is_read"));
    }

    private static String messageId(MailId id) {
        return id.value().toString();
    }
}
