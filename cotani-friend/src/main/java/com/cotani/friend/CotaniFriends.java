package com.cotani.friend;

import com.cotani.event.api.EventBus;
import com.cotani.friend.api.FriendRepository;
import com.cotani.friend.api.FriendService;
import com.cotani.friend.api.FriendServiceOptions;
import com.cotani.friend.api.FriendSnapshot;
import com.cotani.friend.internal.DefaultFriendService;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** Factories for the {@code cotani-friend} module. */
public final class CotaniFriends {
    private CotaniFriends() {}

    /** Creates an isolated in-memory friendship service. */
    public static FriendService inMemory() {
        return create(FriendSnapshot.empty(), null, null, FriendServiceOptions.defaults(), Clock.systemUTC());
    }

    /** Creates an in-memory friendship service with optional event publication. */
    public static FriendService inMemory(@Nullable EventBus eventBus) {
        return create(FriendSnapshot.empty(), null, eventBus, FriendServiceOptions.defaults(), Clock.systemUTC());
    }

    /** Restores a friendship service asynchronously from a repository. */
    public static CompletionStage<FriendService> fromRepositoryAsync(FriendRepository repository) {
        return fromRepositoryAsync(repository, null, FriendServiceOptions.defaults());
    }

    /** Restores a friendship service with explicit options and optional event publication. */
    public static CompletionStage<FriendService> fromRepositoryAsync(
            FriendRepository repository, @Nullable EventBus eventBus, FriendServiceOptions options) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(options, "options");
        return options.withRepositoryTimeout(Objects.requireNonNull(repository.loadAsync(), "repository load stage"))
                .thenApply(snapshot -> create(snapshot, repository, eventBus, options, Clock.systemUTC()));
    }

    private static FriendService create(
            FriendSnapshot snapshot,
            @Nullable FriendRepository repository,
            @Nullable EventBus eventBus,
            FriendServiceOptions options,
            Clock clock) {
        return new DefaultFriendService(snapshot, repository, eventBus, options, clock);
    }
}
