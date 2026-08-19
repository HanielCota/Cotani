package com.cotani.user.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class UserModuleOptionsTest {
    @Test
    void defaultsAreAutoSaveEnabledWithFiveMinuteInterval() {
        UserModuleOptions options = UserModuleOptions.defaults();

        assertTrue(options.autoSaveEnabled());
        assertEquals(Duration.ofMinutes(5), options.autoSaveInterval());
        assertNotNull(options.loadFailureMessage());
    }

    @Test
    void compactConstructorRejectsNullInterval() {
        assertThrows(NullPointerException.class, () -> new UserModuleOptions(true, null, Component.text("fail")));
    }

    @Test
    void compactConstructorRejectsNullMessage() {
        assertThrows(NullPointerException.class, () -> new UserModuleOptions(true, Duration.ofMinutes(5), null));
    }

    @Test
    void compactConstructorAllowsDisabledAutoSave() {
        UserModuleOptions options = new UserModuleOptions(false, Duration.ofMinutes(1), Component.text("fail"));

        assertTrue(!options.autoSaveEnabled());
        assertEquals(Duration.ofMinutes(1), options.autoSaveInterval());
    }
}
