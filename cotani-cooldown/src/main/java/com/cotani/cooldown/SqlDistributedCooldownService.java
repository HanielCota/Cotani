package com.cotani.cooldown;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownResult;
import com.cotani.cooldown.api.DistributedCooldownService;
import com.cotani.cooldown.api.GlobalCooldownTarget;
import com.cotani.cooldown.api.ResourceCooldownTarget;
import com.cotani.cooldown.api.UserCooldownTarget;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.query.Row;
import com.cotani.task.api.DelayedTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SqlDistributedCooldownService implements DistributedCooldownService {
    private static final Logger LOGGER = Logger.getLogger(SqlDistributedCooldownService.class.getName());
    private static final String SELECT_BY_ID = """
        SELECT target_type, target_id, action_name, started_at, expires_at, lease_token
        FROM cotani_cooldowns
        WHERE cooldown_id = ?
        """;
    private static final String KEY_PARAM = "key";
    private static final String DURATION_PARAM = "duration";

    private final CotaniStorage storage;
    private final Clock clock;
    private final SchedulerTask cleanupTask;
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean();

    SqlDistributedCooldownService(
            CotaniStorage storage, DelayedTaskScheduler scheduler, Clock clock, Duration cleanupInterval) {
        this.storage = Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(scheduler, "scheduler");

        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(cleanupInterval, "cleanupInterval");

        if (!cleanupInterval.isPositive()) {
            throw new IllegalArgumentException("cleanupInterval must be positive");
        }

        this.cleanupTask = scheduler.asyncTimer(this::runCleanup, cleanupInterval, cleanupInterval);
    }

    @Override
    public CompletionStage<CooldownResult> checkAndStartAsync(CooldownKey key, Duration duration) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(duration, DURATION_PARAM);

        if (!duration.isPositive()) {
            throw new IllegalArgumentException("duration must be positive");
        }

        var target = TargetColumns.from(key);
        Instant startedAt = clock.instant();
        Instant expiresAt = startedAt.plus(duration);
        String leaseToken = UUID.randomUUID().toString();

        return storage.queryExecutor()
                .update(
                        upsertSql(),
                        binder -> binder.string(target.cooldownId())
                                .string(target.type())
                                .string(target.id())
                                .string(key.action().value())
                                .instant(startedAt)
                                .instant(expiresAt)
                                .string(leaseToken))
                .thenCompose(_ -> findStored(target.cooldownId()))
                .thenApply(stored -> {
                    if (stored.isEmpty()) {
                        return CooldownResult.allowed(key);
                    }
                    var entry = stored.get();
                    if (leaseToken.equals(entry.leaseToken())) {
                        return CooldownResult.allowed(key);
                    }

                    Duration remaining =
                            Duration.between(startedAt, entry.entry().expiresAt());
                    if (remaining.isNegative()) {
                        remaining = Duration.ZERO;
                    }
                    return CooldownResult.denied(key, remaining, entry.entry().expiresAt());
                });
    }

    @Override
    public CompletionStage<Optional<CooldownEntry>> findAsync(CooldownKey key) {
        Objects.requireNonNull(key, KEY_PARAM);

        var target = TargetColumns.from(key);
        Instant now = clock.instant();

        return findStored(target.cooldownId()).thenCompose(stored -> {
            var entry = stored.map(StoredCooldown::entry);

            if (entry.isPresent() && entry.get().expired(now)) {
                return removeExpired(target.cooldownId(), now).thenApply(_ -> Optional.empty());
            }

            return CompletableFuture.completedFuture(entry);
        });
    }

    @Override
    public CompletionStage<Void> removeAsync(CooldownKey key) {
        Objects.requireNonNull(key, KEY_PARAM);

        return storage.queryExecutor()
                .update(
                        "DELETE FROM cotani_cooldowns WHERE cooldown_id = ?",
                        binder -> binder.string(TargetColumns.from(key).cooldownId()));
    }

    @Override
    public CompletionStage<Void> clearExpiredAsync() {
        Instant now = clock.instant();
        return storage.queryExecutor()
                .update("DELETE FROM cotani_cooldowns WHERE expires_at <= ?", binder -> binder.instant(now));
    }

    @Override
    public CompletionStage<Void> clearAllAsync() {
        return storage.queryExecutor().update("DELETE FROM cotani_cooldowns", _ -> {});
    }

    @Override
    public CompletionStage<Long> sizeAsync() {
        return storage.queryExecutor()
                .queryOne(
                        "SELECT COUNT(*) AS entry_count FROM cotani_cooldowns",
                        _ -> {},
                        row -> row.getLong("entry_count"))
                .thenApply(count -> count.orElse(0L));
    }

    @Override
    public void close() {
        cleanupTask.cancel();
    }

    private CompletionStage<Optional<StoredCooldown>> findStored(String cooldownId) {
        return storage.queryExecutor()
                .queryOne(SELECT_BY_ID, binder -> binder.string(cooldownId), this::storedCooldown);
    }

    private StoredCooldown storedCooldown(Row row) throws SQLException {
        String type = row.getStringOptional("target_type")
                .orElseThrow(() -> new IllegalStateException("target_type is SQL NULL"));
        String id = row.getStringOptional("target_id")
                .orElseThrow(() -> new IllegalStateException("target_id is SQL NULL"));
        String actionName = row.getStringOptional("action_name")
                .orElseThrow(() -> new IllegalStateException("action_name is SQL NULL"));
        var key = TargetColumns.toKey(type, id, actionName);
        Instant startedAt = row.getInstantOptional("started_at")
                .orElseThrow(() -> new IllegalStateException("started_at is SQL NULL"));
        Instant expiresAt = row.getInstantOptional("expires_at")
                .orElseThrow(() -> new IllegalStateException("expires_at is SQL NULL"));
        return new StoredCooldown(
                new CooldownEntry(key, startedAt, expiresAt),
                row.getStringOptional("lease_token").orElse(""));
    }

    private CompletionStage<Void> removeExpired(String cooldownId, Instant now) {
        return storage.queryExecutor()
                .update(
                        "DELETE FROM cotani_cooldowns WHERE cooldown_id = ? AND expires_at <= ?",
                        binder -> binder.string(cooldownId).instant(now));
    }

    private String upsertSql() {
        return switch (storage.dialect().name().toLowerCase(Locale.ROOT)) {
            case "mysql" -> """
                INSERT INTO cotani_cooldowns
                    (cooldown_id, target_type, target_id, action_name, started_at, expires_at, lease_token)
                VALUES (?, ?, ?, ?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE
                    lease_token = IF(cotani_cooldowns.expires_at <= incoming.started_at,
                        incoming.lease_token, cotani_cooldowns.lease_token),
                    started_at = IF(cotani_cooldowns.expires_at <= incoming.started_at,
                        incoming.started_at, cotani_cooldowns.started_at),
                    expires_at = IF(cotani_cooldowns.expires_at <= incoming.started_at,
                        incoming.expires_at, cotani_cooldowns.expires_at)
                """;
            case "mariadb" -> """
                INSERT INTO cotani_cooldowns
                    (cooldown_id, target_type, target_id, action_name, started_at, expires_at, lease_token)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    lease_token = IF(expires_at <= VALUES(started_at), VALUES(lease_token), lease_token),
                    started_at = IF(expires_at <= VALUES(started_at), VALUES(started_at), started_at),
                    expires_at = IF(expires_at <= VALUES(started_at), VALUES(expires_at), expires_at)
                """;
            case "sqlite" -> """
                INSERT INTO cotani_cooldowns
                    (cooldown_id, target_type, target_id, action_name, started_at, expires_at, lease_token)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(cooldown_id) DO UPDATE SET
                    lease_token = excluded.lease_token,
                    started_at = excluded.started_at,
                    expires_at = excluded.expires_at
                WHERE cotani_cooldowns.expires_at <= excluded.started_at
                """;
            default ->
                throw new IllegalStateException(
                        "Unsupported cooldown dialect: " + storage.dialect().name());
        };
    }

    private void runCleanup() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return;
        }
        var _ = clearExpiredAsync().whenComplete((_, error) -> {
            cleanupInProgress.set(false);
            if (error != null) {
                LOGGER.log(Level.WARNING, "Could not clean expired distributed cooldowns", error);
            }
        });
    }

    private record StoredCooldown(CooldownEntry entry, String leaseToken) {}

    private record TargetColumns(String cooldownId, String type, String id, CooldownKey key) {
        private static TargetColumns from(CooldownKey key) {
            String type;
            String id;
            switch (key.target()) {
                case UserCooldownTarget user -> {
                    type = "USER";
                    id = user.userId().toString();
                }
                case GlobalCooldownTarget _ -> {
                    type = "GLOBAL";
                    id = "global";
                }
                case ResourceCooldownTarget resource -> {
                    type = "RESOURCE";
                    id = resource.resourceId();
                }
            }
            return new TargetColumns(type + ":" + id + ":" + key.action().value(), type, id, key);
        }

        private static CooldownKey toKey(String type, String id, String actionName) {
            var action = CooldownAction.of(actionName);
            var target =
                    switch (type) {
                        case "USER" -> new UserCooldownTarget(UUID.fromString(id));
                        case "GLOBAL" -> new GlobalCooldownTarget();
                        case "RESOURCE" -> new ResourceCooldownTarget(id);
                        default -> throw new IllegalStateException("Invalid stored cooldown target type: " + type);
                    };

            return new CooldownKey(target, action);
        }
    }
}
