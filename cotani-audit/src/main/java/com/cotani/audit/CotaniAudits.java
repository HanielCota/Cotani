package com.cotani.audit;

import com.cotani.audit.api.AuditRepository;
import com.cotani.audit.api.AuditService;
import com.cotani.audit.internal.DefaultAuditService;
import com.cotani.audit.internal.InMemoryAuditRepository;
import java.util.Objects;

/** Factories for the {@code cotani-audit} module. */
public final class CotaniAudits {
    private CotaniAudits() {}

    /** Creates an isolated in-memory audit service with bounded storage. */
    public static AuditService inMemory() {
        return fromRepository(new InMemoryAuditRepository());
    }

    /** Creates an isolated in-memory audit service with the supplied entry capacity. */
    public static AuditService inMemory(int maxEntries) {
        return fromRepository(new InMemoryAuditRepository(maxEntries));
    }

    /** Creates a service backed by the supplied repository. */
    public static AuditService fromRepository(AuditRepository repository) {
        return new DefaultAuditService(Objects.requireNonNull(repository, "repository"));
    }
}
