package com.cotani.user.api;

import com.cotani.AsyncCloseable;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface UserModule extends AutoCloseable, AsyncCloseable {
    UserService userService();

    @Override
    void close();

    @Override
    CompletionStage<Void> closeAsync();
}
