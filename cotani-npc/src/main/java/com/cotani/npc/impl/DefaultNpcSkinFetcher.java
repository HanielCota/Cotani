package com.cotani.npc.impl;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.NpcSkin;
import com.cotani.npc.api.NpcSkinFetcher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Default non-blocking HTTP implementation of {@link NpcSkinFetcher} with in-memory caching.
 */
@InternalApi
public final class DefaultNpcSkinFetcher implements NpcSkinFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern UUID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient;
    private final Map<String, NpcSkin> cache = new ConcurrentHashMap<>();

    public DefaultNpcSkinFetcher() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public CompletionStage<Optional<NpcSkin>> fetchByUsernameAsync(String username) {
        Objects.requireNonNull(username, "Parameter 'username' must not be null");

        var normalized = username.trim().toLowerCase(java.util.Locale.ROOT);
        var cached = cache.get(normalized);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        var url = "https://api.mojang.com/users/profiles/minecraft/" + username.trim();
        var request =
                HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        return CompletableFuture.completedFuture(Optional.<NpcSkin>empty());
                    }

                    var matcher = UUID_PATTERN.matcher(response.body());
                    if (!matcher.find()) {
                        return CompletableFuture.completedFuture(Optional.<NpcSkin>empty());
                    }

                    var uuid = matcher.group(1);
                    return fetchByUuidAsync(uuid).thenApply(optSkin -> {
                        optSkin.ifPresent(skin -> cache.put(normalized, skin));
                        return optSkin;
                    });
                })
                .exceptionally(_ -> Optional.empty());
    }

    @Override
    public CompletionStage<Optional<NpcSkin>> fetchByUuidAsync(String mojangUuid) {
        Objects.requireNonNull(mojangUuid, "Parameter 'mojangUuid' must not be null");

        var cleanUuid = mojangUuid.replace("-", "").trim();
        var cached = cache.get(cleanUuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        var url = "https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUuid + "?unsigned=false";
        var request =
                HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return Optional.<NpcSkin>empty();
                    }

                    var body = response.body();
                    var valueMatcher = VALUE_PATTERN.matcher(body);
                    var sigMatcher = SIGNATURE_PATTERN.matcher(body);

                    if (valueMatcher.find() && sigMatcher.find()) {
                        var skin = NpcSkin.of(valueMatcher.group(1), sigMatcher.group(1));
                        cache.put(cleanUuid, skin);
                        return Optional.of(skin);
                    }

                    return Optional.<NpcSkin>empty();
                })
                .exceptionally(_ -> Optional.empty());
    }
}
