package com.cotani.user.internal.repository;

import com.cotani.user.internal.model.SimpleCotaniUser;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface UserRepository {

    CompletionStage<Optional<SimpleCotaniUser>> find(UUID uniqueId, String username);

    CompletionStage<Optional<SimpleCotaniUser>> findByUniqueId(UUID uniqueId);

    CompletionStage<Void> save(SimpleCotaniUser user);
}
