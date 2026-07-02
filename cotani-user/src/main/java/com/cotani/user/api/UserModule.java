package com.cotani.user.api;

public interface UserModule extends AutoCloseable {

    UserService userService();

    @Override
    void close();
}
