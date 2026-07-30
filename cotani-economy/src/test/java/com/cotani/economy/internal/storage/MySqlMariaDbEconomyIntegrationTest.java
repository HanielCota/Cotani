package com.cotani.economy.internal.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.exception.InsufficientFundsException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.MariaDbBackend;
import com.cotani.storage.backend.MariaDbCredentials;
import com.cotani.storage.backend.MySqlBackend;
import com.cotani.storage.backend.MySqlCredentials;
import com.cotani.storage.backend.StorageBackend;
import com.cotani.task.api.PaperTaskScheduler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class MySqlMariaDbEconomyIntegrationTest {

    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final EconomyReason REASON = EconomyReason.system("container-integration-test");

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
    void mysqlSerializesCrossInstanceWithdrawalsAndOperationRetries() {
        verifyCrossInstanceAtomicity(() -> mysqlBackend(MYSQL));
    }

    @Test
    void mariaDbSerializesCrossInstanceWithdrawalsAndOperationRetries() {
        verifyCrossInstanceAtomicity(() -> mariaDbBackend(MARIADB));
    }

    private static void verifyCrossInstanceAtomicity(Supplier<StorageBackend> backendFactory) {
        var firstStorage = startStorage(backendFactory.get(), true);
        var secondStorage = startStorage(backendFactory.get(), false);
        try {
            var firstStore = new SqlEconomyStore(firstStorage, Clock.systemUTC(), SETTINGS);
            var secondStore = new SqlEconomyStore(secondStorage, Clock.systemUTC(), SETTINGS);
            var userId = UUID.randomUUID();
            var depositOperation = EconomyOperationId.random();

            var deposit = firstStore
                    .deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, depositOperation)
                    .toCompletableFuture()
                    .join();
            var retry = secondStore
                    .deposit(userId, SETTINGS.defaultCurrency().id(), BigDecimal.TEN, REASON, depositOperation)
                    .toCompletableFuture()
                    .join();
            assertEquals(deposit.id(), retry.id());

            var firstWithdrawal = firstStore.withdraw(
                    userId,
                    SETTINGS.defaultCurrency().id(),
                    BigDecimal.valueOf(7),
                    REASON,
                    EconomyOperationId.random());
            var secondWithdrawal = secondStore.withdraw(
                    userId,
                    SETTINGS.defaultCurrency().id(),
                    BigDecimal.valueOf(7),
                    REASON,
                    EconomyOperationId.random());

            int successes = 0;
            int insufficientFunds = 0;
            for (var operation : List.of(firstWithdrawal, secondWithdrawal)) {
                try {
                    operation.toCompletableFuture().join();
                    successes++;
                } catch (CompletionException failure) {
                    assertInstanceOf(InsufficientFundsException.class, unwrap(failure));
                    insufficientFunds++;
                }
            }

            assertEquals(1, successes);
            assertEquals(1, insufficientFunds);
            var account = firstStore
                    .getOrCreate(userId, SETTINGS.defaultCurrency().id())
                    .toCompletableFuture()
                    .join();
            assertEquals(0, account.balance().compareTo(new BigDecimal("3.00")));
        } finally {
            firstStorage.closeAsync().toCompletableFuture().join();
            secondStorage.closeAsync().toCompletableFuture().join();
        }
    }

    private static CotaniStorage startStorage(StorageBackend backend, boolean migrate) {
        Plugin plugin = mock(Plugin.class);
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor direct = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(direct);
        var builder = CotaniStorage.create(plugin)
                .backend(backend)
                .scheduler(scheduler)
                .virtualThreads()
                .admissionQueueCapacity(16);
        if (migrate) {
            builder.migrations(new CreateEconomyTablesMigration());
        }
        var storage = builder.build();
        storage.startAsync().toCompletableFuture().join();
        return storage;
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
                2, 0, Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(10));
        return new MySqlCredentials(host, port, database, username, password, false, pool);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
