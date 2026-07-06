package com.cotani.cooldown.api;

import java.time.Duration;

@FunctionalInterface
public interface CooldownPolicy {

    Duration duration();
}
