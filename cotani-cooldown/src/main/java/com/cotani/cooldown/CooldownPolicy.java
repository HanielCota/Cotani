package com.cotani.cooldown;

import java.time.Duration;

@FunctionalInterface
public interface CooldownPolicy {

    Duration duration();
}
