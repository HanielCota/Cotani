package com.cotani.cooldown.cache;

import com.cotani.cache.repository.CacheRepository;
import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownTargets;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.SqlConsumer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SqlCooldownRepository implements CacheRepository<UUID, PlayerCooldowns> {

    private final CotaniStorage storage;

    public SqlCooldownRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<Optional<PlayerCooldowns>> find(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        var sql =
                "SELECT action_name, started_at, expires_at FROM cotani_cooldowns WHERE target_type = 'USER' AND target_id = ?";
        return storage.transactions()
                .run(tx -> tx.queryMany(sql, binder -> binder.string(playerId.toString()), row -> {
                            String action = row.getString("action_name");
                            Instant startedAt = Objects.requireNonNull(row.getInstant("started_at"));
                            Instant expiresAt = Objects.requireNonNull(row.getInstant("expires_at"));
                            return new CooldownEntry(
                                    new CooldownKey(CooldownTargets.user(playerId), CooldownAction.of(action)),
                                    startedAt,
                                    expiresAt);
                        })
                        .thenApply(entries -> {
                            Instant now = Instant.now();
                            Map<String, CooldownEntry> map = new ConcurrentHashMap<>();
                            for (CooldownEntry entry : entries) {
                                if (entry.expired(now)) {
                                    continue;
                                }
                                map.put(entry.key().action().value(), entry);
                            }
                            return Optional.of(new PlayerCooldowns(playerId, map));
                        }));
    }

    @Override
    public CompletionStage<Void> save(UUID playerId, PlayerCooldowns value) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(value, "value");

        return storage.transactions().run(tx -> {
            var deleteSql = "DELETE FROM cotani_cooldowns WHERE target_type = 'USER' AND target_id = ?";
            var insertSql =
                    "INSERT INTO cotani_cooldowns (cooldown_id, target_type, target_id, action_name, started_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";

            return tx.update(deleteSql, binder -> binder.string(playerId.toString()))
                    .thenCompose(_ -> {
                        Instant now = Instant.now();
                        List<SqlConsumer<ParameterBinder>> binders = new ArrayList<>();
                        for (CooldownEntry entry : value.activeCooldowns().values()) {
                            if (entry.expired(now)) {
                                continue;
                            }
                            binders.add(binder -> {
                                String cooldownId = "USER:" + playerId + ":"
                                        + entry.key().action().value();
                                binder.string(cooldownId)
                                        .string("USER")
                                        .string(playerId.toString())
                                        .string(entry.key().action().value())
                                        .instant(entry.startedAt())
                                        .instant(entry.expiresAt());
                            });
                        }
                        if (binders.isEmpty()) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return tx.batch(insertSql, binders);
                    });
        });
    }

    @Override
    public CompletionStage<Void> delete(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        var sql = "DELETE FROM cotani_cooldowns WHERE target_type = 'USER' AND target_id = ?";
        return storage.transactions().run(tx -> tx.update(sql, binder -> binder.string(playerId.toString())));
    }
}
