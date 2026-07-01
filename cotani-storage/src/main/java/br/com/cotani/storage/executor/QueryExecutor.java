package br.com.cotani.storage.executor;

import br.com.cotani.storage.error.QueryError;
import br.com.cotani.storage.error.StorageException;
import br.com.cotani.storage.future.OptionalStorageFuture;
import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.provider.StorageProvider;
import br.com.cotani.storage.query.EntityMapper;
import br.com.cotani.storage.query.ParameterBinder;
import br.com.cotani.storage.query.Row;
import br.com.cotani.storage.query.SqlConsumer;
import br.com.cotani.storage.scheduler.CotaniScheduler;
import br.com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class QueryExecutor {

    private final StorageProvider provider;
    private final Executor executor;
    private final CotaniScheduler scheduler;
    private final ValueSerializerRegistry serializers;

    public QueryExecutor(
            StorageProvider provider,
            Executor executor,
            CotaniScheduler scheduler,
            ValueSerializerRegistry serializers) {
        this.provider = provider;
        this.executor = executor;
        this.scheduler = scheduler;
        this.serializers = serializers;
    }

    public StorageFuture<Void> update(String sql, SqlConsumer<ParameterBinder> binder) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> runUpdate(sql, binder), executor);
        return new StorageFuture<>(future, scheduler);
    }

    public <T> StorageFuture<Optional<T>> queryOne(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        CompletableFuture<Optional<T>> future =
                CompletableFuture.supplyAsync(() -> runQueryOne(sql, binder, mapper), executor);
        return new StorageFuture<>(future, scheduler);
    }

    public <T> OptionalStorageFuture<T> optionalQueryOne(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        return new OptionalStorageFuture<>(queryOne(sql, binder, mapper), scheduler);
    }

    public <T> StorageFuture<List<T>> queryMany(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        CompletableFuture<List<T>> future =
                CompletableFuture.supplyAsync(() -> runQueryMany(sql, binder, mapper), executor);
        return new StorageFuture<>(future, scheduler);
    }

    public <T> StorageFuture<T> completed(T value) {
        return StorageFuture.completed(value, scheduler);
    }

    public StorageFuture<Boolean> exists(String sql, SqlConsumer<ParameterBinder> binder) {
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> runExists(sql, binder), executor);
        return new StorageFuture<>(future, scheduler);
    }

    public StorageFuture<Void> batch(String sql, List<SqlConsumer<ParameterBinder>> binders) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> runBatch(sql, binders), executor);
        return new StorageFuture<>(future, scheduler);
    }

    public Executor executor() {
        return executor;
    }

    private void runUpdate(String sql, SqlConsumer<ParameterBinder> binder) {
        try (Connection connection = provider.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(new ParameterBinder(statement, serializers));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute update query.", exception));
        }
    }

    private <T> Optional<T> runQueryOne(String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        try (Connection connection = provider.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(new ParameterBinder(statement, serializers));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapper.map(new Row(resultSet, serializers)));
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute select query.", exception));
        }
    }

    private <T> List<T> runQueryMany(String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        try (Connection connection = provider.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(new ParameterBinder(statement, serializers));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(mapper.map(new Row(resultSet, serializers)));
                }
                return values;
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute list query.", exception));
        }
    }

    private boolean runExists(String sql, SqlConsumer<ParameterBinder> binder) {
        try (Connection connection = provider.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(new ParameterBinder(statement, serializers));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute exists query.", exception));
        }
    }

    private void runBatch(String sql, List<SqlConsumer<ParameterBinder>> binders) {
        try (Connection connection = provider.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            ParameterBinder binder = new ParameterBinder(statement, serializers);
            int count = 0;
            for (SqlConsumer<ParameterBinder> item : binders) {
                item.accept(binder);
                statement.addBatch();
                count++;
                if (count % 1000 == 0) {
                    statement.executeBatch();
                }
            }
            if (count % 1000 != 0) {
                statement.executeBatch();
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute batch query.", exception));
        }
    }
}
