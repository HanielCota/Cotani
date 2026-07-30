package com.cotani.storage.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.schema.Schema;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class MigrationRunnerTest {

    private final QueryExecutor executor = createExecutor();
    private final Schema schema =
            new Schema(executor, org.mockito.Mockito.mock(com.cotani.storage.dialect.SqlDialect.class));

    @SuppressWarnings("NullAway")
    private static Migration migration(int version, String description) {
        return new Migration() {
            @Override
            public int version() {
                return version;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public CompletionStage<Void> migrate(Schema schema) {
                return CompletableFuture.completedStage(null);
            }
        };
    }

    private static QueryExecutor createExecutor() {
        var provider = org.mockito.Mockito.mock(StorageProvider.class);
        return new QueryExecutor(provider, Runnable::run, new ValueSerializerRegistry());
    }

    @Test
    void rejectsDuplicateVersions() {
        var runner = new MigrationRunner(executor, schema);
        runner.add(migration(1, "first"));
        assertThrows(IllegalArgumentException.class, () -> runner.add(migration(1, "duplicate")));
    }

    @Test
    void acceptsDifferentVersions() {
        var runner = new MigrationRunner(executor, schema);
        runner.add(migration(1, "first"));
        assertDoesNotThrow(() -> runner.add(migration(2, "second")));
    }

    @Test
    void acceptsSameVersionInDifferentNamespaces() {
        var runner = new MigrationRunner(executor, schema);
        runner.add(migration(1, "first"));
        var otherNamespace = new Migration() {
            @Override
            public String namespace() {
                return "another-module";
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public String description() {
                return "other";
            }

            @Override
            public CompletionStage<Void> migrate(Schema schema) {
                return CompletableFuture.completedStage(null);
            }
        };

        assertDoesNotThrow(() -> runner.add(otherNamespace));
    }
}
