package com.cotani.teleport.policy;

import com.cotani.teleport.api.TeleportFailureReason;
import net.kyori.adventure.text.Component;

public sealed interface PolicyResult permits PolicyResult.Allowed, PolicyResult.Denied {
    record Allowed() implements PolicyResult {}

    record Denied(TeleportFailureReason reason, Component message) implements PolicyResult {}

    static Allowed allowed() {
        return new Allowed();
    }

    static Denied denied(TeleportFailureReason reason, Component message) {
        return new Denied(reason, message);
    }
}
