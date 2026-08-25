package com.cotani.party;

import com.cotani.event.api.EventBus;
import com.cotani.party.api.Party;
import com.cotani.party.api.PartyRepository;
import com.cotani.party.api.PartyService;
import com.cotani.party.api.PartyServiceOptions;
import com.cotani.party.internal.DefaultPartyService;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** Factories for the {@code cotani-party} module. */
public final class CotaniParties {
    private CotaniParties() {}

    /** Creates an isolated in-memory party service. */
    public static PartyService inMemory() {
        return create(List.of(), null, null, PartyServiceOptions.defaults(), Clock.systemUTC());
    }

    /** Creates an in-memory service with optional domain-event publication. */
    public static PartyService inMemory(EventBus eventBus) {
        return create(List.of(), null, eventBus, PartyServiceOptions.defaults(), Clock.systemUTC());
    }

    /** Restores parties asynchronously from a repository. */
    public static CompletionStage<PartyService> fromRepositoryAsync(PartyRepository repository) {
        return fromRepositoryAsync(repository, null, PartyServiceOptions.defaults());
    }

    /** Restores parties asynchronously with event publication and explicit repository options. */
    public static CompletionStage<PartyService> fromRepositoryAsync(
            PartyRepository repository, @Nullable EventBus eventBus, PartyServiceOptions options) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(options, "options");
        return options.withRepositoryTimeout(Objects.requireNonNull(repository.loadAsync(), "repository load stage"))
                .thenApply(snapshot -> create(snapshot.parties(), repository, eventBus, options, Clock.systemUTC()));
    }

    private static PartyService create(
            List<Party> initialParties,
            @Nullable PartyRepository repository,
            @Nullable EventBus eventBus,
            PartyServiceOptions options,
            Clock clock) {
        return new DefaultPartyService(initialParties, repository, eventBus, options, clock);
    }
}
