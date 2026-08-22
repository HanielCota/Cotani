package com.cotani.command.api;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Evaluates and enforces cooldowns on command execution.
 */
public interface CooldownEvaluator {
    /**
     * Checks if a cooldown is currently active for the sender.
     *
     * @param sender the command sender
     * @param commandName the canonical command name
     * @return remaining duration if on cooldown, empty otherwise
     */
    Optional<Duration> check(CommandSender sender, String commandName);

    /**
     * Applies the cooldown for the sender.
     *
     * @param sender the command sender
     * @param commandName the canonical command name
     */
    void apply(CommandSender sender, String commandName);

    /**
     * Returns a no-op cooldown evaluator (no cooldown enforced).
     *
     * @return no-op evaluator
     */
    static CooldownEvaluator none() {
        return NoopCooldownEvaluator.INSTANCE;
    }

    /**
     * Creates an in-memory duration-based cooldown evaluator.
     *
     * @param duration the cooldown duration
     * @return the cooldown evaluator
     */
    static CooldownEvaluator of(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            return none();
        }
        return new InMemoryDurationCooldownEvaluator(duration);
    }

    /**
     * Creates a cooldown evaluator backed by {@link com.cotani.cooldown.api.CooldownService}.
     *
     * @param cooldownService the cooldown service
     * @param duration the cooldown duration
     * @return the cooldown evaluator
     */
    static CooldownEvaluator of(com.cotani.cooldown.api.CooldownService cooldownService, Duration duration) {
        Objects.requireNonNull(cooldownService, "cooldownService");
        Objects.requireNonNull(duration, "duration");
        return new ServiceCooldownEvaluator(cooldownService, duration);
    }
}

final class ServiceCooldownEvaluator implements CooldownEvaluator {
    private final com.cotani.cooldown.api.CooldownService cooldownService;
    private final Duration duration;

    ServiceCooldownEvaluator(com.cotani.cooldown.api.CooldownService cooldownService, Duration duration) {
        this.cooldownService = cooldownService;
        this.duration = duration;
    }

    @Override
    public Optional<Duration> check(CommandSender sender, String commandName) {
        String action = "cmd:" + commandName.toLowerCase(java.util.Locale.ROOT);
        if (sender instanceof Player player) {
            return cooldownService.user(player.getUniqueId()).action(action).remaining();
        }
        return cooldownService.global().action(action).remaining();
    }

    @Override
    public void apply(CommandSender sender, String commandName) {
        Objects.requireNonNull(sender, "Parameter 'sender' must not be null");
        Objects.requireNonNull(commandName, "Parameter 'commandName' must not be null");
        String action = "cmd:" + commandName.toLowerCase(java.util.Locale.ROOT);
        if (sender instanceof Player player) {
            var _ = cooldownService
                    .user(player.getUniqueId())
                    .action(action)
                    .duration(duration)
                    .start();
            return;
        }
        var _ = cooldownService.global().action(action).duration(duration).start();
    }
}

final class NoopCooldownEvaluator implements CooldownEvaluator {
    static final NoopCooldownEvaluator INSTANCE = new NoopCooldownEvaluator();

    private NoopCooldownEvaluator() {}

    @Override
    public Optional<Duration> check(CommandSender sender, String commandName) {
        return Optional.empty();
    }

    @Override
    public void apply(CommandSender sender, String commandName) {}
}

final class InMemoryDurationCooldownEvaluator implements CooldownEvaluator {
    private final Duration duration;
    private final Map<String, Long> expiryNanos = new ConcurrentHashMap<>();

    InMemoryDurationCooldownEvaluator(Duration duration) {
        this.duration = duration;
    }

    @Override
    public Optional<Duration> check(CommandSender sender, String commandName) {
        var key = resolveKey(sender, commandName);
        var expiry = expiryNanos.get(key);
        if (expiry == null) {
            return Optional.empty();
        }

        var now = System.nanoTime();
        var remainingNanos = expiry - now;
        if (remainingNanos <= 0) {
            expiryNanos.remove(key, expiry);
            return Optional.empty();
        }

        return Optional.of(Duration.ofNanos(remainingNanos));
    }

    @Override
    public void apply(CommandSender sender, String commandName) {
        var key = resolveKey(sender, commandName);
        var expiry = System.nanoTime() + duration.toNanos();
        expiryNanos.put(key, expiry);
    }

    private static String resolveKey(CommandSender sender, String commandName) {
        var normalizedName = commandName.toLowerCase(java.util.Locale.ROOT);
        if (sender instanceof Player player) {
            return player.getUniqueId() + ":" + normalizedName;
        }
        return "console:" + normalizedName;
    }
}
