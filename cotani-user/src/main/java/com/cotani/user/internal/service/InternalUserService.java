package com.cotani.user.internal.service;

import com.cotani.api.InternalApi;
import com.cotani.user.api.UserService;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@InternalApi
public interface InternalUserService extends UserService {
    CompletionStage<SimpleCotaniUser> load(UUID uniqueId, String username);

    CompletionStage<Void> unload(UUID uniqueId);

    CompletionStage<Void> save(UUID uniqueId);

    CompletionStage<Void> saveAll();
}
