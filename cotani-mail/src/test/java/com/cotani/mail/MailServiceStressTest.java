package com.cotani.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.mail.api.MailId;
import com.cotani.mail.api.MailQuery;
import com.cotani.mail.api.MailSendRequest;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class MailServiceStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final UUID RECIPIENT = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Test
    void variedMessagesRemainIdempotentAndPageWithoutDuplicates() {
        var service = CotaniMails.inMemory();
        try {
            StressTestSupport.scenarios("mail", "send-idempotent-page", (context, random, sender) -> {
                var request = new MailSendRequest(
                        new MailId(random.uuid("mail")),
                        sender.id(),
                        RECIPIENT,
                        "subject-" + context.iteration(),
                        "body-" + random.input(128),
                        Instant.now().plusSeconds(context.iteration()),
                        Duration.ofDays(365));
                var first = StressTestSupport.await(service.sendAsync(request), TIMEOUT, context);
                var repeated = StressTestSupport.await(service.sendAsync(request), TIMEOUT, context);
                assertEquals(first, repeated, context::description);
            });

            int expected = StressTestSupport.iterations();
            var ids = new HashSet<MailId>();
            for (int page = 0; ids.size() < expected; page++) {
                var result = service.inboxAsync(RECIPIENT, new MailQuery(page, 50, false))
                        .toCompletableFuture()
                        .join();
                result.messages()
                        .forEach(message -> assertTrue(ids.add(message.id()), "duplicate mail " + message.id()));
                if (!result.hasMore()) {
                    break;
                }
            }
            assertEquals(expected, ids.size());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void oneIdempotencyKeySentOneThousandTimesCreatesOneMessage() {
        var service = CotaniMails.inMemory();
        var request = new MailSendRequest(
                new MailId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                RECIPIENT,
                "same request",
                "same body",
                Instant.now(),
                Duration.ofDays(365));
        try {
            var results = StressTestSupport.concurrent(
                    "mail",
                    "duplicate-send",
                    StressTestSupport.MINIMUM_ITERATIONS,
                    32,
                    TIMEOUT,
                    index -> service.sendAsync(request));
            assertEquals(1, results.stream().distinct().count());
            assertEquals(
                    1,
                    service.inboxAsync(RECIPIENT, MailQuery.firstPage(10))
                            .toCompletableFuture()
                            .join()
                            .messages()
                            .size());
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}
