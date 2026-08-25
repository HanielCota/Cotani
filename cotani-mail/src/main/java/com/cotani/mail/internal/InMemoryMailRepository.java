package com.cotani.mail.internal;

import com.cotani.api.InternalApi;
import com.cotani.mail.api.MailConflictException;
import com.cotani.mail.api.MailId;
import com.cotani.mail.api.MailMessage;
import com.cotani.mail.api.MailNotFoundException;
import com.cotani.mail.api.MailPage;
import com.cotani.mail.api.MailQuery;
import com.cotani.mail.api.MailRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@InternalApi
public final class InMemoryMailRepository implements MailRepository {
    private final Map<MailId, MailMessage> messages = new LinkedHashMap<>();

    @Override
    public synchronized CompletionStage<MailMessage> saveAsync(MailMessage message) {
        Objects.requireNonNull(message, "message");
        var previous = messages.get(message.id());
        if (previous != null && !previous.hasSameContentAs(message)) {
            return CompletableFuture.failedFuture(new MailConflictException(message.id()));
        }
        messages.putIfAbsent(message.id(), message);
        return CompletableFuture.completedFuture(messages.get(message.id()));
    }

    @Override
    public synchronized CompletionStage<MailPage> inboxAsync(UUID recipientId, MailQuery query, Instant now) {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(now, "now");
        var visible = messages.values().stream()
                .filter(message -> message.recipientId().equals(recipientId))
                .filter(message -> !message.isExpired(now))
                .sorted(Comparator.comparing(MailMessage::sentAt)
                        .reversed()
                        .thenComparing(message -> message.id().value().toString(), Comparator.reverseOrder()))
                .toList();
        var unreadCount =
                (int) visible.stream().filter(message -> !message.read()).count();
        var filtered = query.unreadOnly()
                ? visible.stream().filter(message -> !message.read()).toList()
                : visible;
        long offset = (long) query.page() * query.pageSize();
        var page = filtered.stream()
                .skip(offset)
                .limit((long) query.pageSize() + 1)
                .toList();
        var hasMore = page.size() > query.pageSize();
        var messagesOnPage = new ArrayList<>(page.subList(0, Math.min(page.size(), query.pageSize())));
        return CompletableFuture.completedFuture(new MailPage(messagesOnPage, hasMore, unreadCount));
    }

    @Override
    public synchronized CompletionStage<MailMessage> markReadAsync(UUID recipientId, MailId id, Instant readAt) {
        Objects.requireNonNull(readAt, "readAt");
        var message = findOwned(recipientId, id);
        if (message.isExpired(readAt)) {
            messages.remove(id);
            return CompletableFuture.failedFuture(new MailNotFoundException(recipientId, id));
        }
        var updated = message.markRead();
        messages.put(id, updated);
        return CompletableFuture.completedFuture(updated);
    }

    @Override
    public synchronized CompletionStage<Void> deleteAsync(UUID recipientId, MailId id) {
        findOwned(recipientId, id);
        messages.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletionStage<Void> purgeExpiredAsync(Instant now) {
        Objects.requireNonNull(now, "now");
        messages.values().removeIf(message -> message.isExpired(now));
        return CompletableFuture.completedFuture(null);
    }

    private MailMessage findOwned(UUID recipientId, MailId id) {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(id, "id");
        var message = messages.get(id);
        if (message == null || !message.recipientId().equals(recipientId)) {
            throw new MailNotFoundException(recipientId, id);
        }
        return message;
    }
}
