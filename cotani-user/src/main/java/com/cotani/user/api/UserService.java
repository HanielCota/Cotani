package com.cotani.user.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Public asynchronous API exposed by cotani-user to other modules.
 */
public interface UserService {

    CompletionStage<Optional<CotaniUser>> findAsync(UUID uniqueId);

    CompletionStage<CotaniUser> getOrThrowAsync(UUID uniqueId);

    CompletionStage<Boolean> isLoadedAsync(UUID uniqueId);
}
