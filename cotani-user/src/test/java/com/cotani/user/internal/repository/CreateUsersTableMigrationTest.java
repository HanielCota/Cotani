package com.cotani.user.internal.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.storage.dialect.SQLiteDialect;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateUsersTableMigrationTest {
    private final QueryExecutor executor = mock(QueryExecutor.class);
    private final Schema schema = new Schema(executor, new SQLiteDialect());
    private final CreateUsersTableMigration migration = new CreateUsersTableMigration();

    @Test
    void declaresVersionOneWithDescriptiveName() {
        assertEquals(1, migration.version());
        assertEquals("Create Cotani users table", migration.description());
    }

    @Test
    void implementsMigrationContract() {
        assertInstanceOf(Migration.class, migration);
    }

    @Test
    void createsUsersTableWithPrimaryKeyAndNotNullColumns() throws Exception {
        when(executor.update(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        migration.migrate(schema).toCompletableFuture().get(5, TimeUnit.SECONDS);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(executor).update(sql.capture(), any());

        var dialect = new SQLiteDialect();
        String expected = "CREATE TABLE IF NOT EXISTS cotani_users ("
                + "unique_id " + dialect.type("UUID", 255) + " PRIMARY KEY NOT NULL, "
                + "username " + dialect.type("STRING", 255) + " NOT NULL, "
                + "first_join_at " + dialect.type("LONG", 255) + " NOT NULL, "
                + "last_join_at " + dialect.type("LONG", 255) + " NOT NULL, "
                + "last_quit_at " + dialect.type("LONG", 255) + " NOT NULL, "
                + "version " + dialect.type("LONG", 255) + " NOT NULL)";
        assertEquals(expected, sql.getValue());
    }

    @Test
    void migrationIsIdempotentByDefault() throws Exception {
        when(executor.update(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        migration.migrate(schema).toCompletableFuture().get(5, TimeUnit.SECONDS);
        migration.migrate(schema).toCompletableFuture().get(5, TimeUnit.SECONDS);

        verify(executor, org.mockito.Mockito.times(2)).update(anyString(), any());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(executor, org.mockito.Mockito.times(2)).update(sql.capture(), any());
        assertEquals(sql.getAllValues().getFirst(), sql.getAllValues().getLast());
        assertTrue(sql.getValue().startsWith("CREATE TABLE IF NOT EXISTS"));
    }
}
