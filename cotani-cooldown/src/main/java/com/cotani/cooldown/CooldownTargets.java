package com.cotani.cooldown;

import java.util.UUID;

public final class CooldownTargets {

    private CooldownTargets() {
        throw new UnsupportedOperationException("utility class");
    }

    public static CooldownTarget user(UUID userId) {
        return new UserCooldownTarget(userId);
    }

    public static CooldownTarget global() {
        return new GlobalCooldownTarget();
    }

    public static CooldownTarget resource(String resourceId) {
        return new ResourceCooldownTarget(resourceId);
    }
}
