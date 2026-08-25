package com.cotani.audit.storage;

import com.cotani.audit.CotaniAudits;
import com.cotani.audit.api.AuditService;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import java.util.List;
import java.util.Objects;

/** Factory and migration entry point for the SQL audit adapter. */
public final class CotaniAuditStorages {
    private CotaniAuditStorages() {}

    /** Creates an audit service using a started Cotani Storage instance. */
    public static AuditService create(CotaniStorage storage) {
        Objects.requireNonNull(storage, "storage");
        return CotaniAudits.fromRepository(new StorageAuditRepository(storage));
    }

    /** Returns the migration required before creating a storage-backed audit service. */
    public static List<Migration> migrations() {
        return StorageAuditRepository.migrations();
    }
}
