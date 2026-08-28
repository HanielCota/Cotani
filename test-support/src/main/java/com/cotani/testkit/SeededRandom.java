package com.cotani.testkit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** Reproducible pseudo-random values for property-style tests. */
public final class SeededRandom {
    private static final char[] INPUT_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-.:<>/&\\\"'".toCharArray();
    private final long seed;
    private final Random random;

    private SeededRandom(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public static SeededRandom scenario(long rootSeed, String module, String operation, int iteration) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(operation, "operation");
        var identity = module + ':' + operation + ':' + iteration;
        var identityId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        return new SeededRandom(rootSeed ^ identityId.getMostSignificantBits() ^ identityId.getLeastSignificantBits());
    }

    public long seed() {
        return seed;
    }

    public UUID uuid(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        var value = namespace + ':' + seed + ':' + random.nextLong();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    public int nextInt(int origin, int bound) {
        return random.nextInt(origin, bound);
    }

    public long nextLong(long origin, long bound) {
        return random.nextLong(origin, bound);
    }

    public String input(int maximumLength) {
        if (maximumLength < 0) {
            throw new IllegalArgumentException("maximumLength cannot be negative");
        }
        int length = random.nextInt(maximumLength + 1);
        var value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append(INPUT_ALPHABET[random.nextInt(INPUT_ALPHABET.length)]);
        }
        return value.toString();
    }

    public BigDecimal positiveDecimal(int maximumIntegralValue, int maximumScale) {
        if (maximumIntegralValue < 1 || maximumScale < 0) {
            throw new IllegalArgumentException("invalid decimal bounds");
        }
        int scale = random.nextInt(maximumScale + 1);
        var unscaledBound = BigDecimal.valueOf(maximumIntegralValue)
                .movePointRight(scale)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
        return BigDecimal.valueOf(random.nextLong(1, unscaledBound + 1), scale);
    }

    public Duration duration(Duration minimum, Duration maximum) {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        long minimumMillis = minimum.toMillis();
        long maximumMillis = maximum.toMillis();
        if (minimumMillis > maximumMillis) {
            throw new IllegalArgumentException("minimum duration must not exceed maximum duration");
        }
        return Duration.ofMillis(random.nextLong(minimumMillis, maximumMillis + 1));
    }

    public <T> T choose(List<T> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values cannot be empty");
        }
        return values.get(random.nextInt(values.size()));
    }
}
