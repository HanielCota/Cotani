package com.cotani.punishment;

import com.cotani.audit.api.AuditService;
import com.cotani.punishment.api.Punishment;
import com.cotani.punishment.api.PunishmentRepository;
import com.cotani.punishment.api.PunishmentService;
import com.cotani.punishment.api.PunishmentServiceOptions;
import com.cotani.punishment.internal.DefaultPunishmentService;
import com.cotani.punishment.storage.StoragePunishmentRepository;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** Factories for the {@code cotani-punishment} module. */
public final class CotaniPunishments {
    private CotaniPunishments() {}

    /** Creates an isolated in-memory service. */
    public static PunishmentService inMemory() {
        return fromInitial(List.of(), null, null, PunishmentServiceOptions.defaults());
    }

    /** Creates an in-memory service with optional audit integration. */
    public static PunishmentService inMemory(@Nullable AuditService auditService) {
        return fromInitial(List.of(), null, auditService, PunishmentServiceOptions.defaults());
    }

    /** Creates a repository-backed service without eagerly loading the complete punishment history. */
    public static CompletionStage<PunishmentService> fromRepositoryAsync(PunishmentRepository repository) {
        return fromRepositoryAsync(repository, PunishmentServiceOptions.defaults(), null);
    }

    /** Loads punishments asynchronously with explicit timeout and audit integration. */
    public static CompletionStage<PunishmentService> fromRepositoryAsync(
            PunishmentRepository repository, PunishmentServiceOptions options, @Nullable AuditService auditService) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(options, "options");
        return CompletableFuture.completedFuture(fromInitial(List.of(), repository, auditService, options));
    }

    /** Loads punishments asynchronously from Cotani Storage. */
    public static CompletionStage<PunishmentService> storageAsync(CotaniStorage storage) {
        return fromRepositoryAsync(new StoragePunishmentRepository(storage));
    }

    /** Loads punishments from Cotani Storage and records successful mutations in the audit trail. */
    public static CompletionStage<PunishmentService> storageAsync(CotaniStorage storage, AuditService auditService) {
        return fromRepositoryAsync(
                new StoragePunishmentRepository(storage), PunishmentServiceOptions.defaults(), auditService);
    }

    /** Returns the migrations required by the SQL repository. */
    public static List<Migration> migrations() {
        return StoragePunishmentRepository.migrations();
    }

    private static PunishmentService fromInitial(
            List<Punishment> values,
            @Nullable PunishmentRepository repository,
            @Nullable AuditService auditService,
            PunishmentServiceOptions options) {
        return new DefaultPunishmentService(values, repository, auditService, options);
    }
}
