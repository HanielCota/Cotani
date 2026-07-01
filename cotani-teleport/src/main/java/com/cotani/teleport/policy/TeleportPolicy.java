package com.cotani.teleport.policy;

import com.cotani.teleport.api.TeleportContext;

public interface TeleportPolicy {
    PolicyResult validate(TeleportContext context);
}
