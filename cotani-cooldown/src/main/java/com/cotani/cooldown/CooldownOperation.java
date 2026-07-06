package com.cotani.cooldown;

import java.time.Duration;
import java.util.Optional;

public interface CooldownOperation {

    CooldownOperation action(String action);

    CooldownOperation action(CooldownAction action);

    CooldownOperation duration(Duration duration);

    CooldownOperation policy(CooldownPolicy policy);

    CooldownResult check();

    CooldownResult start();

    CooldownResult restart();

    CooldownResult checkAndStart();

    Optional<Duration> remaining();

    boolean active();

    void remove();
}
