package com.cotani.cooldown;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public interface CooldownService {

    CooldownOperation target(CooldownTarget target);

    default CooldownOperation user(UUID userId) {
        return target(CooldownTargets.user(userId));
    }

    default CooldownOperation global() {
        return target(CooldownTargets.global());
    }

    default CooldownOperation resource(String resourceId) {
        return target(CooldownTargets.resource(resourceId));
    }

    default boolean deny(UUID userId, String action, Duration duration) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(duration, "duration cannot be null");

        return user(userId).action(action).duration(duration).checkAndStart().denied();
    }

    default boolean allow(UUID userId, String action, Duration duration) {
        return !deny(userId, action, duration);
    }

    void remove(CooldownTarget target, CooldownAction action);

    void clearExpired();

    void clearAll();
}
