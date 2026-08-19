package com.cotani.economy.internal.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.storage.dialect.SQLiteDialect;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateEconomyTablesMigrationTest {

    @Test
    void shouldExposeVersionOneWithDescriptiveMetadata() {
        var migration = new CreateEconomyTablesMigration();

        assertEquals(1, migration.version());
        assertEquals("Create Cotani economy accounts and transactions tables", migration.description());
        assertEquals(CreateEconomyTablesMigration.class.getPackageName(), migration.namespace());
    }

    @Test
    void shouldCreateAccountsTableWithCompositePrimaryKeyAndTransactionsTableWithUniqueOperationId() throws Exception {
        var executor = mock(QueryExecutor.class);
        when(executor.update(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        var schema = new Schema(executor, new SQLiteDialect());

        new CreateEconomyTablesMigration().migrate(schema).toCompletableFuture().get(5, TimeUnit.SECONDS);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(executor, org.mockito.Mockito.times(2)).update(captor.capture(), any());
        var statements = captor.getAllValues();

        assertEquals(2, statements.size());
        var accountsSql = statements.get(0);
        var transactionsSql = statements.get(1);

        assertTrue(accountsSql.contains("CREATE TABLE IF NOT EXISTS cotani_economy_accounts"));
        assertTrue(accountsSql.contains("user_id"));
        assertTrue(accountsSql.contains("currency_id"));
        assertTrue(accountsSql.contains("balance"));
        assertTrue(accountsSql.contains("created_at"));
        assertTrue(accountsSql.contains("updated_at"));
        assertTrue(accountsSql.contains("PRIMARY KEY (user_id, currency_id)"));

        assertTrue(transactionsSql.contains("CREATE TABLE IF NOT EXISTS cotani_economy_transactions"));
        assertTrue(transactionsSql.contains("transaction_id"));
        assertTrue(transactionsSql.contains("operation_id"));
        assertTrue(transactionsSql.contains("UNIQUE"));
        assertTrue(transactionsSql.contains("source_user_id"));
        assertTrue(transactionsSql.contains("target_user_id"));
        assertTrue(transactionsSql.contains("amount"));
        assertTrue(transactionsSql.contains("reason_key"));
        assertTrue(transactionsSql.contains("reason_source"));
        assertTrue(transactionsSql.contains("reason_actor_user_id"));
    }

    @Test
    void shouldCreateAccountsTableBeforeTransactionsTable() throws Exception {
        var executor = mock(QueryExecutor.class);
        when(executor.update(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        var schema = new Schema(executor, new SQLiteDialect());

        new CreateEconomyTablesMigration().migrate(schema).toCompletableFuture().get(5, TimeUnit.SECONDS);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(executor, org.mockito.Mockito.times(2)).update(captor.capture(), any());
        var statements = captor.getAllValues();

        assertTrue(statements.get(0).contains("cotani_economy_accounts"));
        assertTrue(statements.get(1).contains("cotani_economy_transactions"));
    }

    @Test
    void shouldGenerateStableSqlAcrossRuns() throws Exception {
        var executor = mock(QueryExecutor.class);
        when(executor.update(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        var schema = new Schema(executor, new SQLiteDialect());

        new CreateEconomyTablesMigration().migrate(schema).toCompletableFuture().get(5, TimeUnit.SECONDS);
        new CreateEconomyTablesMigration().migrate(schema).toCompletableFuture().get(5, TimeUnit.SECONDS);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(executor, org.mockito.Mockito.times(4)).update(captor.capture(), any());
        var statements = captor.getAllValues();

        assertEquals(statements.get(0), statements.get(2));
        assertEquals(statements.get(1), statements.get(3));
    }
}
