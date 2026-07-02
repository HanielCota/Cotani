package com.cotani.user.api;

import com.cotani.text.MiniMessages;
import java.time.Duration;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public record UserModuleOptions(boolean autoSaveEnabled, Duration autoSaveInterval, Component loadFailureMessage) {

    private static final Duration DEFAULT_AUTO_SAVE_INTERVAL = Duration.ofMinutes(5);
    private static final Component DEFAULT_LOAD_FAILURE_MESSAGE =
            MiniMessages.parse("<red>Não foi possível carregar seus dados. Tente novamente.");

    public UserModuleOptions {
        Objects.requireNonNull(autoSaveInterval, "autoSaveInterval");
        Objects.requireNonNull(loadFailureMessage, "loadFailureMessage");
    }

    public static UserModuleOptions defaults() {
        return new UserModuleOptions(true, DEFAULT_AUTO_SAVE_INTERVAL, DEFAULT_LOAD_FAILURE_MESSAGE);
    }
}
