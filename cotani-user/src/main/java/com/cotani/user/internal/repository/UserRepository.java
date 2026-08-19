package com.cotani.user.internal.repository;

import com.cotani.api.InternalApi;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@InternalApi
public interface UserRepository {
    CompletionStage<Optional<SimpleCotaniUser>> find(UUID uniqueId, String username);

    CompletionStage<Optional<SimpleCotaniUser>> findByUniqueId(UUID uniqueId);

    CompletionStage<Optional<SimpleCotaniUser>> findByUsername(String username);

    CompletionStage<Void> save(SimpleCotaniUser user);

    CompletionStage<Void> saveAll(Collection<SimpleCotaniUser> users);
}
