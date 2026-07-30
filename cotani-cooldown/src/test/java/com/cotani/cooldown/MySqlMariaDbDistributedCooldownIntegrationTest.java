package com.cotani.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownResult;
import com.cotani.cooldown.api.CooldownTargets;
import com.cotani.cooldown.api.DistributedCooldownService;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.MariaDbBackend;
import com.cotani.storage.backend.MariaDbCredentials;
import com.cotani.storage.backend.MySqlBackend;
import com.cotani.storage.backend.MySqlCredentials;
import com.cotani.storage.backend.StorageBackend;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class MySqlMariaDbDistributedCooldownIntegrationTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.6")
            .withDatabaseName("cotani")
            .withUsername("cotani")
            .withPassword("cotani-test");

    @Container
    private static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4.8")
            .withDatabaseName("cotani")
            .withUsername("cotani")
            .withPassword("cotani-test");

    @Test
    void mysqlAllowsExactlyOneCrossInstanceAcquisition() {
        verifyCrossInstanceAtomicity(() -> mysqlBackend(MYSQL));
    }

    @Test
    void mariaDbAllowsExactlyOneCrossInstanceAcquisition() {
        verifyCrossInstanceAtomicity(() -> mariaDbBackend(MARIADB));
    }

    private static void verifyCrossInstanceAtomicity(Supplier<StorageBackend> backendFactory) {
        var scheduler = scheduler();
        var firstStorage = startStorage(backendFactory.get(), scheduler, true);
        var secondStorage = startStorage(backendFactory.get(), scheduler, false);
        try (DistributedCooldownService first = CotaniCooldowns.distributed(firstStorage, scheduler);
                DistributedCooldownService second = CotaniCooldowns.distributed(secondStorage, scheduler)) {
            var key = new CooldownKey(CooldownTargets.resource("world:spawn"), CooldownAction.of("reward:daily"));
            var attempts = new ArrayList<CompletionStage<CooldownResult>>();
            for (int i = 0; i < 32; i++) {
                attempts.add(((i & 1) == 0 ? first : second).checkAndStartAsync(key, Duration.ofMinutes(5)));
            }

            long allowed = attempts.stream()
                    .map(stage -> stage.toCompletableFuture().join())
                    .filter(CooldownResult::allowed)
                    .count();

            assertEquals(1, allowed);
            assertEquals(
                    key,
                    second.findAsync(key)
                            .toCompletableFuture()
                            .join()
                            .orElseThrow()
                            .key());
        } finally {
            firstStorage.closeAsync().toCompletableFuture().join();
            secondStorage.closeAsync().toCompletableFuture().join();
        }
    }

    private static CotaniStorage startStorage(StorageBackend backend, PaperTaskScheduler scheduler, boolean migrate) {
        var builder = CotaniStorage.create(mock(Plugin.class))
                .backend(backend)
                .scheduler(scheduler)
                .virtualThreads()
                .admissionQueueCapacity(64);
        if (migrate) {
            builder.migrations(CotaniCooldowns.migrations().toArray(Migration[]::new));
        }
        var storage = builder.build();
        storage.startAsync().toCompletableFuture().join();
        return storage;
    }

    private static PaperTaskScheduler scheduler() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor direct = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(direct);
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
        return scheduler;
    }

    private static MySqlBackend mysqlBackend(MySQLContainer container) {
        return new MySqlBackend(credentials(
                container.getHost(),
                container.getFirstMappedPort(),
                container.getDatabaseName(),
                container.getUsername(),
                container.getPassword()));
    }

    private static MariaDbBackend mariaDbBackend(MariaDBContainer container) {
        return new MariaDbBackend(new MariaDbCredentials(credentials(
                container.getHost(),
                container.getFirstMappedPort(),
                container.getDatabaseName(),
                container.getUsername(),
                container.getPassword())));
    }

    private static MySqlCredentials credentials(
            String host, int port, String database, String username, String password) {
        var pool = new MySqlCredentials.PoolSettings(
                4, 0, Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(10));
        return new MySqlCredentials(host, port, database, username, password, false, pool);
    }
}
