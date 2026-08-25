package com.cotani.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.mail.api.MailConflictException;
import com.cotani.mail.api.MailExpiredException;
import com.cotani.mail.api.MailId;
import com.cotani.mail.api.MailMessage;
import com.cotani.mail.api.MailPage;
import com.cotani.mail.api.MailQuery;
import com.cotani.mail.api.MailRepository;
import com.cotani.mail.api.MailSendRequest;
import com.cotani.mail.api.MailService;
import com.cotani.mail.api.MailServiceOptions;
import com.cotani.mail.internal.InMemoryMailRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class MailServiceTest {
    private static final UUID SENDER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECIPIENT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void sendsIdempotentlyPaginatesAndManagesReadState() {
        var clock = new MutableClock(START);
        var service = CotaniMails.fromRepository(
                new InMemoryMailRepository(),
                new MailServiceOptions(10, Duration.ofDays(1), Duration.ofSeconds(1)),
                clock);
        var firstRequest = new MailSendRequest(
                new MailId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                SENDER,
                RECIPIENT,
                "First",
                "Hello",
                START,
                Duration.ofHours(1));

        var first = service.sendAsync(firstRequest).toCompletableFuture().join();
        assertEquals(
                first, service.sendAsync(firstRequest).toCompletableFuture().join());
        service.sendAsync(SENDER, RECIPIENT, "Other", "Reply")
                .toCompletableFuture()
                .join();

        var page = service.inboxAsync(RECIPIENT, MailQuery.firstPage(1))
                .toCompletableFuture()
                .join();
        assertEquals(1, page.messages().size());
        assertTrue(page.hasMore());
        assertEquals(2, page.unreadCount());

        service.markReadAsync(RECIPIENT, first.id()).toCompletableFuture().join();
        var unread = service.inboxAsync(RECIPIENT, MailQuery.firstPage(10).unread())
                .toCompletableFuture()
                .join();
        assertEquals(1, unread.messages().size());
        assertEquals(1, unread.unreadCount());
        assertTrue(unread.messages().stream().noneMatch(message -> message.id().equals(first.id())));

        service.deleteAsync(RECIPIENT, first.id()).toCompletableFuture().join();
        assertEquals(
                1,
                service.inboxAsync(RECIPIENT, MailQuery.firstPage(10))
                        .toCompletableFuture()
                        .join()
                        .messages()
                        .size());
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentContent() {
        var service = CotaniMails.fromRepository(
                new InMemoryMailRepository(), MailServiceOptions.defaults(), Clock.fixed(START, ZoneId.of("UTC")));
        var id = MailId.random();
        service.sendAsync(new MailSendRequest(id, SENDER, RECIPIENT, "Same id", "One", START, Duration.ofDays(1)))
                .toCompletableFuture()
                .join();

        var failure = assertThrows(
                CompletionException.class,
                () -> service.sendAsync(
                                new MailSendRequest(id, SENDER, RECIPIENT, "Same id", "Two", START, Duration.ofDays(1)))
                        .toCompletableFuture()
                        .join());
        assertTrue(failure.getCause() instanceof MailConflictException);
    }

    @Test
    void omitsAndPurgesExpiredMessages() {
        var clock = new MutableClock(START);
        MailService service =
                CotaniMails.fromRepository(new InMemoryMailRepository(), MailServiceOptions.defaults(), clock);
        service.sendAsync(new MailSendRequest(
                        MailId.random(), SENDER, RECIPIENT, "Temporary", "Expires", START, Duration.ofSeconds(1)))
                .toCompletableFuture()
                .join();

        clock.advance(Duration.ofSeconds(2));
        assertTrue(service.inboxAsync(RECIPIENT, MailQuery.firstPage(10))
                .toCompletableFuture()
                .join()
                .messages()
                .isEmpty());
        service.purgeExpiredAsync().toCompletableFuture().join();
        assertFalse(service.inboxAsync(RECIPIENT, MailQuery.firstPage(10))
                .toCompletableFuture()
                .join()
                .hasMore());
    }

    @Test
    void inboxWaitsForPreviouslyAcceptedMutation() {
        var repository = new BlockingRepository();
        var service = CotaniMails.fromRepository(
                repository,
                new MailServiceOptions(10, Duration.ofDays(1), Duration.ofSeconds(1)),
                Clock.fixed(START, ZoneId.of("UTC")));
        var send = service.sendAsync(
                new MailSendRequest(MailId.random(), SENDER, RECIPIENT, "Pending", "Wait", START, Duration.ofDays(1)));
        var inbox = service.inboxAsync(RECIPIENT, MailQuery.firstPage(10));

        assertFalse(repository.inboxCalled.get());
        assertFalse(inbox.toCompletableFuture().isDone());

        repository.completeSave();

        send.toCompletableFuture().join();
        inbox.toCompletableFuture().join();
        assertTrue(repository.inboxCalled.get());
    }

    @Test
    void rejectsAlreadyExpiredSendRequests() {
        var service = CotaniMails.fromRepository(
                new InMemoryMailRepository(), MailServiceOptions.defaults(), Clock.fixed(START, ZoneId.of("UTC")));
        var failure = assertThrows(
                CompletionException.class,
                () -> service.sendAsync(new MailSendRequest(
                                MailId.random(),
                                SENDER,
                                RECIPIENT,
                                "Expired",
                                "Too late",
                                START.minusSeconds(2),
                                Duration.ofSeconds(1)))
                        .toCompletableFuture()
                        .join());
        assertTrue(failure.getCause() instanceof MailExpiredException);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }

    private static final class BlockingRepository implements MailRepository {
        private final CompletableFuture<MailMessage> save = new CompletableFuture<>();
        private final AtomicBoolean inboxCalled = new AtomicBoolean();
        private @Nullable MailMessage pending;

        @Override
        public CompletionStage<MailMessage> saveAsync(MailMessage message) {
            pending = message;
            return save;
        }

        @Override
        public CompletionStage<MailPage> inboxAsync(UUID recipientId, MailQuery query, Instant now) {
            inboxCalled.set(true);
            return CompletableFuture.completedFuture(new MailPage(List.of(), false, 0));
        }

        @Override
        public CompletionStage<MailMessage> markReadAsync(UUID recipientId, MailId id, Instant readAt) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<Void> deleteAsync(UUID recipientId, MailId id) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletionStage<Void> purgeExpiredAsync(Instant now) {
            return CompletableFuture.completedFuture(null);
        }

        private void completeSave() {
            save.complete(Objects.requireNonNull(pending, "pending"));
        }
    }
}
