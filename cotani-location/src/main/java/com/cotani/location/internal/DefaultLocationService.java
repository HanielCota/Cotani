package com.cotani.location.internal;

import com.cotani.api.InternalApi;
import com.cotani.location.api.Home;
import com.cotani.location.api.HomeId;
import com.cotani.location.api.HomeLimitExceededException;
import com.cotani.location.api.HomeNotFoundException;
import com.cotani.location.api.LocationName;
import com.cotani.location.api.LocationPosition;
import com.cotani.location.api.LocationRepository;
import com.cotani.location.api.LocationService;
import com.cotani.location.api.LocationServiceOptions;
import com.cotani.location.api.LocationSnapshot;
import com.cotani.location.api.Warp;
import com.cotani.location.api.WarpId;
import com.cotani.location.api.WarpNotFoundException;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultLocationService implements LocationService {
    private static final Logger LOGGER = Logger.getLogger(DefaultLocationService.class.getName());

    private final Object stateLock = new Object();
    private final Map<HomeId, Home> homes = new LinkedHashMap<>();
    private final Map<WarpId, Warp> warps = new LinkedHashMap<>();
    private final @Nullable LocationRepository repository;
    private final LocationServiceOptions options;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();
    private @Nullable CompletionStage<Void> closeStage;

    private DefaultLocationService(
            LocationSnapshot snapshot,
            @Nullable LocationRepository repository,
            LocationServiceOptions options,
            Clock clock) {
        this.repository = repository;
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(snapshot, "snapshot");
        snapshot.homes().forEach(home -> homes.put(home.id(), home));
        snapshot.warps().forEach(warp -> warps.put(warp.id(), warp));
    }

    public static DefaultLocationService inMemory(LocationServiceOptions options, Clock clock) {
        return create(LocationSnapshot.empty(), null, options, clock);
    }

    public static DefaultLocationService create(
            LocationSnapshot snapshot,
            @Nullable LocationRepository repository,
            LocationServiceOptions options,
            Clock clock) {
        return new DefaultLocationService(snapshot, repository, options, clock);
    }

    @Override
    public CompletionStage<Optional<Home>> findHomeAsync(UUID ownerId, LocationName name) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(name, "name");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(Optional.ofNullable(homes.get(new HomeId(ownerId, name))));
        }
    }

    @Override
    public CompletionStage<List<Home>> homesAsync(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(homes.values().stream()
                    .filter(home -> home.id().ownerId().equals(ownerId))
                    .sorted(Comparator.comparing(home -> home.id().name().value()))
                    .toList());
        }
    }

    @Override
    public CompletionStage<Home> setHomeAsync(UUID ownerId, LocationName name, LocationPosition position) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        return enqueue(() -> {
            var id = new HomeId(ownerId, name);
            Home previous;
            synchronized (stateLock) {
                previous = homes.get(id);
                if (previous == null
                        && homes.values().stream()
                                        .filter(home -> home.id().ownerId().equals(ownerId))
                                        .count()
                                >= options.maxHomesPerPlayer()) {
                    throw new HomeLimitExceededException(ownerId, options.maxHomesPerPlayer());
                }
            }
            var now = clock.instant();
            var home = new Home(id, position, previous == null ? now : previous.createdAt(), now);
            return afterDurable(saveHome(home), () -> {
                synchronized (stateLock) {
                    homes.put(id, home);
                }
                return home;
            });
        });
    }

    @Override
    public CompletionStage<Void> deleteHomeAsync(UUID ownerId, LocationName name) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(name, "name");
        return enqueue(() -> {
            var id = new HomeId(ownerId, name);
            synchronized (stateLock) {
                if (!homes.containsKey(id)) {
                    return failedMutation(new HomeNotFoundException(id));
                }
            }
            return afterDurableVoid(deleteHome(id), () -> {
                synchronized (stateLock) {
                    homes.remove(id);
                }
            });
        });
    }

    @Override
    public CompletionStage<Optional<Warp>> findWarpAsync(LocationName name) {
        Objects.requireNonNull(name, "name");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(Optional.ofNullable(warps.get(new WarpId(name))));
        }
    }

    @Override
    public CompletionStage<List<Warp>> warpsAsync() {
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(warps.values().stream()
                    .sorted(Comparator.comparing(warp -> warp.id().name().value()))
                    .toList());
        }
    }

    @Override
    public CompletionStage<Warp> setWarpAsync(LocationName name, LocationPosition position) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        return enqueue(() -> {
            var id = new WarpId(name);
            Warp previous;
            synchronized (stateLock) {
                previous = warps.get(id);
            }
            var now = clock.instant();
            var warp = new Warp(id, position, previous == null ? now : previous.createdAt(), now);
            return afterDurable(saveWarp(warp), () -> {
                synchronized (stateLock) {
                    warps.put(id, warp);
                }
                return warp;
            });
        });
    }

    @Override
    public CompletionStage<Void> deleteWarpAsync(LocationName name) {
        Objects.requireNonNull(name, "name");
        return enqueue(() -> {
            var id = new WarpId(name);
            synchronized (stateLock) {
                if (!warps.containsKey(id)) {
                    return failedMutation(new WarpNotFoundException(id));
                }
            }
            return afterDurableVoid(deleteWarp(id), () -> {
                synchronized (stateLock) {
                    warps.remove(id);
                }
            });
        });
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (stateLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            closeStage = lastOperation.whenComplete((ignored, failure) -> {
                synchronized (stateLock) {
                    homes.clear();
                    warps.clear();
                }
            });
            return closeStage;
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close location service", failure);
            }
        });
    }

    private CompletionStage<Void> saveHome(Home home) {
        if (repository == null) {
            return completedVoid();
        }
        return Objects.requireNonNull(repository.saveHomeAsync(home), "repository save home stage");
    }

    private CompletionStage<Void> deleteHome(HomeId id) {
        if (repository == null) {
            return completedVoid();
        }
        return Objects.requireNonNull(repository.deleteHomeAsync(id), "repository delete home stage");
    }

    private CompletionStage<Void> saveWarp(Warp warp) {
        if (repository == null) {
            return completedVoid();
        }
        return Objects.requireNonNull(repository.saveWarpAsync(warp), "repository save warp stage");
    }

    private CompletionStage<Void> deleteWarp(WarpId id) {
        if (repository == null) {
            return completedVoid();
        }
        return Objects.requireNonNull(repository.deleteWarpAsync(id), "repository delete warp stage");
    }

    private <T> Mutation<T> afterDurable(CompletionStage<Void> durable, Supplier<T> stateUpdate) {
        var updated = durable.thenApply(ignored -> stateUpdate.get());
        return new Mutation<>(options.withRepositoryTimeout(updated), updated.thenApply(ignored -> null));
    }

    private Mutation<Void> afterDurableVoid(CompletionStage<Void> durable, Runnable stateUpdate) {
        var updated = durable.thenRun(stateUpdate);
        return new Mutation<>(options.withRepositoryTimeout(updated), updated);
    }

    private <T> CompletionStage<T> enqueue(Supplier<Mutation<T>> operation) {
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }

            var result = new CompletableFuture<T>();
            var barrier = new CompletableFuture<Void>();
            var predecessor = sequencingTail;
            predecessor.whenComplete((ignored, failure) -> {
                Mutation<T> mutation;
                try {
                    mutation = Objects.requireNonNull(operation.get(), "operation");
                } catch (RuntimeException operationFailure) {
                    result.completeExceptionally(operationFailure);
                    barrier.completeExceptionally(operationFailure);
                    return;
                }

                mutation.result().whenComplete((value, operationFailure) -> {
                    if (operationFailure == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(operationFailure);
                    }
                });
                mutation.barrier().whenComplete((value, operationFailure) -> {
                    if (operationFailure == null) {
                        barrier.complete(null);
                    } else {
                        barrier.completeExceptionally(operationFailure);
                    }
                });
            });
            sequencingTail = barrier.handle((ignored, failure) -> null);
            lastOperation = barrier;
            return result;
        }
    }

    private static <T> Mutation<T> failedMutation(Throwable failure) {
        var failed = CompletableFuture.<T>failedFuture(failure);
        return new Mutation<>(failed, CompletableFuture.failedFuture(failure));
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Location service is closed");
    }

    private record Mutation<T>(CompletionStage<T> result, CompletionStage<Void> barrier) {
        private Mutation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(barrier, "barrier");
        }
    }
}
