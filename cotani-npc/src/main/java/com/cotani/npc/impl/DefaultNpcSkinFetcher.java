package com.cotani.npc.impl;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.NpcSkin;
import com.cotani.npc.api.NpcSkinFetcher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Default non-blocking HTTP implementation of {@link NpcSkinFetcher} with in-memory caching.
 */
@InternalApi
public final class DefaultNpcSkinFetcher implements NpcSkinFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern INPUT_UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern UUID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");
    private static final int MAX_CACHE_ENTRIES = 1_024;

    private final HttpClient httpClient;
    private final Map<String, Optional<NpcSkin>> cache =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Optional<NpcSkin>> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });
    private final Map<String, CompletionStage<Optional<NpcSkin>>> inFlight = new ConcurrentHashMap<>();
    private final Semaphore requestPermits = new Semaphore(16);

    public DefaultNpcSkinFetcher() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public CompletionStage<Optional<NpcSkin>> fetchByUsernameAsync(String username) {
        Objects.requireNonNull(username, "Parameter 'username' must not be null");

        var normalized = username.trim().toLowerCase(java.util.Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        var cacheKey = "name:" + normalized;
        var cached = cache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        var url = "https://api.mojang.com/users/profiles/minecraft/" + normalized;
        var request =
                HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();

        return deduplicate(
                cacheKey,
                () -> executeBounded(() -> httpClient
                        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenCompose(response -> {
                            if (response.statusCode() != 200) {
                                return CompletableFuture.completedFuture(cacheResult(cacheKey, Optional.empty()));
                            }

                            var matcher = UUID_PATTERN.matcher(response.body());
                            if (!matcher.find()) {
                                return CompletableFuture.completedFuture(cacheResult(cacheKey, Optional.empty()));
                            }

                            var uuid = matcher.group(1);
                            return fetchByUuidAsync(uuid).thenApply(optSkin -> {
                                cache.put(cacheKey, optSkin);
                                return optSkin;
                            });
                        })));
    }

    @Override
    public CompletionStage<Optional<NpcSkin>> fetchByUuidAsync(String mojangUuid) {
        Objects.requireNonNull(mojangUuid, "Parameter 'mojangUuid' must not be null");

        var cleanUuid = mojangUuid.replace("-", "").trim();
        if (!INPUT_UUID_PATTERN.matcher(cleanUuid).matches()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        var cacheKey = "uuid:" + cleanUuid.toLowerCase(java.util.Locale.ROOT);
        var cached = cache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        var url = "https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUuid + "?unsigned=false";
        var request =
                HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();

        return deduplicate(
                cacheKey,
                () -> executeBounded(() -> httpClient
                        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> {
                            if (response.statusCode() != 200) {
                                return cacheResult(cacheKey, Optional.<NpcSkin>empty());
                            }

                            var body = response.body();
                            var valueMatcher = VALUE_PATTERN.matcher(body);
                            var sigMatcher = SIGNATURE_PATTERN.matcher(body);

                            if (valueMatcher.find() && sigMatcher.find()) {
                                var skin = NpcSkin.of(valueMatcher.group(1), sigMatcher.group(1));
                                return cacheResult(cacheKey, Optional.of(skin));
                            }

                            return cacheResult(cacheKey, Optional.<NpcSkin>empty());
                        })));
    }

    private CompletionStage<Optional<NpcSkin>> deduplicate(
            String key, Supplier<CompletionStage<Optional<NpcSkin>>> operation) {
        var stage = inFlight.computeIfAbsent(key, ignored -> operation.get());
        return stage.whenComplete((_, _) -> inFlight.remove(key, stage));
    }

    private CompletionStage<Optional<NpcSkin>> executeBounded(Supplier<CompletionStage<Optional<NpcSkin>>> operation) {
        if (!requestPermits.tryAcquire()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        try {
            return operation
                    .get()
                    .toCompletableFuture()
                    .orTimeout(TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(_ -> Optional.empty())
                    .whenComplete((_, _) -> requestPermits.release());
        } catch (RuntimeException failure) {
            requestPermits.release();
            return CompletableFuture.failedFuture(failure);
        }
    }

    private Optional<NpcSkin> cacheResult(String key, Optional<NpcSkin> result) {
        cache.put(key, result);
        return result;
    }
}
