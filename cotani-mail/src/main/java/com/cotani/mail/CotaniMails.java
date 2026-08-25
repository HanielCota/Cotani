package com.cotani.mail;

import com.cotani.mail.api.MailRepository;
import com.cotani.mail.api.MailService;
import com.cotani.mail.api.MailServiceOptions;
import com.cotani.mail.internal.DefaultMailService;
import java.time.Clock;
import java.util.Objects;

/** Factories for the {@code cotani-mail} module. */
public final class CotaniMails {
    private CotaniMails() {}

    /** Creates a mail service backed by an isolated in-memory repository. */
    public static MailService inMemory() {
        return inMemory(MailServiceOptions.defaults());
    }

    /** Creates an in-memory mail service with explicit options. */
    public static MailService inMemory(MailServiceOptions options) {
        Objects.requireNonNull(options, "options");
        return DefaultMailService.create(
                new com.cotani.mail.internal.InMemoryMailRepository(), options, Clock.systemUTC());
    }

    /** Creates a service over a caller-owned repository. */
    public static MailService fromRepository(MailRepository repository) {
        return fromRepository(repository, MailServiceOptions.defaults());
    }

    /** Creates a service over a caller-owned repository with explicit options. */
    public static MailService fromRepository(MailRepository repository, MailServiceOptions options) {
        return fromRepository(repository, options, Clock.systemUTC());
    }

    static MailService fromRepository(MailRepository repository, MailServiceOptions options, Clock clock) {
        return DefaultMailService.create(
                Objects.requireNonNull(repository, "repository"),
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(clock, "clock"));
    }
}
