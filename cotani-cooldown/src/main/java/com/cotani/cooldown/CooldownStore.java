package com.cotani.cooldown;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

public interface CooldownStore {

    Optional<CooldownEntry> find(CooldownKey key);

    void save(CooldownEntry entry);

    void remove(CooldownKey key);

    void removeExpired(Clock clock);

    void clear();

    CooldownResult checkAndStart(CooldownKey key, Duration duration, Clock clock);
}
