package com.cotani.placeholder.impl;

import com.cotani.api.InternalApi;
import com.cotani.placeholder.api.AsyncPlaceholderContext;
import com.cotani.placeholder.api.AsyncPlaceholderHandler;
import com.cotani.placeholder.api.PlaceholderContext;
import com.cotani.placeholder.api.PlaceholderExpansion;
import com.cotani.placeholder.api.PlaceholderHandler;
import com.cotani.placeholder.api.PlaceholderService;
import com.cotani.placeholder.api.RelationalPlaceholderHandler;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.text.MiniMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of the {@link PlaceholderService}.
 */
@InternalApi
@NullMarked
public final class DefaultPlaceholderService implements PlaceholderService {

    private final Logger logger;
    private final PaperTaskScheduler scheduler;
    private final PlaceholderApiBridge papiBridge;
    private final Map<String, PlaceholderExpansion> expansions = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile String[] sortedIdentifiers = new String[0];

    public DefaultPlaceholderService(Plugin plugin, PaperTaskScheduler scheduler) {
        var requiredPlugin = Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        this.logger = requiredPlugin.getLogger();
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
        this.papiBridge = new PlaceholderApiBridge(requiredPlugin);

        // Register default expansions
        register(new BuiltinPlayerExpansion());
        register(new BuiltinServerExpansion());

        // Hook PAPI if available
        this.papiBridge.register(this);
    }

    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public void register(PlaceholderExpansion expansion) {
        Objects.requireNonNull(expansion, "Parameter 'expansion' must not be null");
        ensureNotClosed();
        expansions.put(expansion.identifier().toLowerCase(java.util.Locale.ROOT), expansion);
        rebuildSortedIdentifiers();
    }

    @Override
    public void register(String identifier, PlaceholderHandler handler) {
        Objects.requireNonNull(identifier, "Parameter 'identifier' must not be null");
        Objects.requireNonNull(handler, "Parameter 'handler' must not be null");
        register(FunctionalExpansion.ofSync(identifier, handler));
    }

    @Override
    public void registerAsync(String identifier, AsyncPlaceholderHandler handler) {
        Objects.requireNonNull(identifier, "Parameter 'identifier' must not be null");
        Objects.requireNonNull(handler, "Parameter 'handler' must not be null");
        register(FunctionalExpansion.ofAsync(identifier, handler));
    }

    @Override
    public void registerRelational(String identifier, RelationalPlaceholderHandler handler) {
        Objects.requireNonNull(identifier, "Parameter 'identifier' must not be null");
        Objects.requireNonNull(handler, "Parameter 'handler' must not be null");
        register(FunctionalExpansion.ofRelational(identifier, handler));
    }

    @Override
    public boolean unregister(String identifier) {
        Objects.requireNonNull(identifier, "Parameter 'identifier' must not be null");
        boolean removed = expansions.remove(identifier.toLowerCase(java.util.Locale.ROOT)) != null;
        if (removed) {
            rebuildSortedIdentifiers();
        }
        return removed;
    }

    private void rebuildSortedIdentifiers() {
        this.sortedIdentifiers = expansions.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toArray(String[]::new);
    }

    @Override
    public Optional<PlaceholderExpansion> findExpansion(String identifier) {
        Objects.requireNonNull(identifier, "Parameter 'identifier' must not be null");
        return Optional.ofNullable(expansions.get(identifier.toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public Set<String> expansions() {
        return Set.copyOf(expansions.keySet());
    }

    @Override
    public String parse(String text) {
        return parse(PlaceholderContext.empty(), text);
    }

    @Override
    public String parse(@Nullable Player player, String text) {
        var context = player == null ? PlaceholderContext.empty() : PlaceholderContext.of(player.getUniqueId());
        return parse(context, text);
    }

    @Override
    public String parse(@Nullable UUID playerUuid, String text) {
        return parse(PlaceholderContext.of(playerUuid), text);
    }

    @Override
    public String parse(PlaceholderContext context, String text) {
        Objects.requireNonNull(context, "Parameter 'context' must not be null");
        Objects.requireNonNull(text, "Parameter 'text' must not be null");

        if (text.isEmpty()) {
            return text;
        }

        return parseSynchronously(context, text, null, null);
    }

    @Override
    public String parseRelational(Player viewer, Player target, String text) {
        Objects.requireNonNull(viewer, "Parameter 'viewer' must not be null");
        Objects.requireNonNull(target, "Parameter 'target' must not be null");
        Objects.requireNonNull(text, "Parameter 'text' must not be null");

        return parseSynchronously(
                PlaceholderContext.relational(viewer.getUniqueId(), target.getUniqueId()), text, viewer, target);
    }

    @Override
    public String parseRelational(UUID viewerId, UUID targetId, String text) {
        Objects.requireNonNull(viewerId, "Parameter 'viewerId' must not be null");
        Objects.requireNonNull(targetId, "Parameter 'targetId' must not be null");
        Objects.requireNonNull(text, "Parameter 'text' must not be null");

        return parse(PlaceholderContext.relational(viewerId, targetId), text);
    }

    @Override
    public CompletionStage<String> parseAsync(String text) {
        return parseAsync(PlaceholderContext.empty(), text);
    }

    @Override
    public CompletionStage<String> parseAsync(@Nullable Player player, String text) {
        var context = player == null ? PlaceholderContext.empty() : PlaceholderContext.of(player.getUniqueId());
        return parseAsync(context, text);
    }

    @Override
    public CompletionStage<String> parseAsync(@Nullable UUID playerUuid, String text) {
        return parseAsync(PlaceholderContext.of(playerUuid), text);
    }

    @Override
    public CompletionStage<String> parseAsync(PlaceholderContext context, String text) {
        Objects.requireNonNull(context, "Parameter 'context' must not be null");
        Objects.requireNonNull(text, "Parameter 'text' must not be null");

        if (text.isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }

        List<FastPlaceholderParser.TokenMatch> tokens = FastPlaceholderParser.findTokens(text);
        if (tokens.isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }

        List<CompletionStage<@Nullable String>> stages = new ArrayList<>(tokens.size());
        for (FastPlaceholderParser.TokenMatch token : tokens) {
            stages.add(resolveTokenAsync(context, token.innerToken()));
        }

        CompletionStage<List<@Nullable String>> resolvedValues =
                CompletableFuture.completedFuture(new ArrayList<>(tokens.size()));
        for (CompletionStage<@Nullable String> stage : stages) {
            resolvedValues = resolvedValues.thenCombine(stage, (values, value) -> {
                var updated = new ArrayList<@Nullable String>(values.size() + 1);
                updated.addAll(values);
                updated.add(value);
                return updated;
            });
        }

        return resolvedValues.thenApply(values -> {
            var sb = new StringBuilder(text.length() + 32);
            var lastIndex = 0;

            for (var i = 0; i < tokens.size(); i++) {
                FastPlaceholderParser.TokenMatch token = tokens.get(i);
                sb.append(text, lastIndex, token.startIndex());

                String replacement = values.get(i);
                if (replacement == null) {
                    sb.append(token.fullToken());
                    lastIndex = token.endIndex();
                    continue;
                }

                sb.append(replacement);

                lastIndex = token.endIndex();
            }

            if (lastIndex < text.length()) {
                sb.append(text, lastIndex, text.length());
            }

            return sb.toString();
        });
    }

    @Override
    public CompletionStage<String> parseRelationalAsync(Player viewer, Player target, String text) {
        Objects.requireNonNull(viewer, "Parameter 'viewer' must not be null");
        Objects.requireNonNull(target, "Parameter 'target' must not be null");
        return parseAsync(PlaceholderContext.relational(viewer.getUniqueId(), target.getUniqueId()), text);
    }

    @Override
    public CompletionStage<String> parseRelationalAsync(UUID viewerId, UUID targetId, String text) {
        Objects.requireNonNull(viewerId, "Parameter 'viewerId' must not be null");
        Objects.requireNonNull(targetId, "Parameter 'targetId' must not be null");
        return parseAsync(PlaceholderContext.relational(viewerId, targetId), text);
    }

    @Override
    public Component parseComponent(String text) {
        return parseComponent(PlaceholderContext.empty(), text);
    }

    @Override
    public Component parseComponent(@Nullable Player player, String text) {
        var context = player == null ? PlaceholderContext.empty() : PlaceholderContext.of(player.getUniqueId());
        return parseComponent(context, text);
    }

    @Override
    public Component parseComponent(PlaceholderContext context, String text) {
        Objects.requireNonNull(context, "Parameter 'context' must not be null");
        Objects.requireNonNull(text, "Parameter 'text' must not be null");

        String resolved = parse(context, text);
        return MiniMessages.parse(resolved);
    }

    @Override
    public TagResolver tagResolver(PlaceholderContext context) {
        Objects.requireNonNull(context, "Parameter 'context' must not be null");

        return new TagResolver() {
            @Override
            public @Nullable Tag resolve(
                    String name,
                    net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue args,
                    net.kyori.adventure.text.minimessage.Context ctx) {
                String explicitParam = args.hasNext() ? args.popOr("").value() : null;

                if (explicitParam != null) {
                    PlaceholderExpansion expansion = expansions.get(name.toLowerCase(java.util.Locale.ROOT));
                    if (expansion != null) {
                        String result = expansion.onContextRequest(context, explicitParam);
                        if (result != null) {
                            return Tag.preProcessParsed(result);
                        }
                    }
                }

                // If no explicit arguments (e.g. <player_name>), match via longest prefix
                var match = findMatchingExpansion(name);
                if (match != null) {
                    String result = match.expansion().onContextRequest(context, match.params());
                    if (result != null) {
                        return Tag.preProcessParsed(result);
                    }
                }

                return null;
            }

            @Override
            public boolean has(String name) {
                return expansions.containsKey(name.toLowerCase(java.util.Locale.ROOT))
                        || findMatchingExpansion(name) != null;
            }
        };
    }

    private String parseSynchronously(
            PlaceholderContext context, String text, @Nullable Player directViewer, @Nullable Player directTarget) {
        return FastPlaceholderParser.replaceTokens(
                text, innerToken -> resolveToken(context, innerToken, directViewer, directTarget));
    }

    private @Nullable String resolveToken(
            PlaceholderContext context,
            String innerToken,
            @Nullable Player directViewer,
            @Nullable Player directTarget) {
        // Check for relational placeholder prefix (rel_)
        if (innerToken.startsWith("rel_") || innerToken.startsWith("rel:")) {
            String stripped = innerToken.substring(4);
            var match = findMatchingExpansion(stripped);

            if (match != null) {
                Optional<Player> viewer = directViewer == null ? context.viewer() : Optional.of(directViewer);
                Optional<Player> target = directTarget == null ? context.target() : Optional.of(directTarget);
                if (viewer.isPresent() && target.isPresent()) {
                    String res = match.expansion().onRelationalRequest(viewer.get(), target.get(), match.params());
                    if (res != null) {
                        return res;
                    }
                }
            }

            Optional<Player> viewer = directViewer == null ? context.viewer() : Optional.of(directViewer);
            Optional<Player> target = directTarget == null ? context.target() : Optional.of(directTarget);
            if (viewer.isPresent() && target.isPresent()) {
                return papiBridge.resolveExternalRelational(viewer.get(), target.get(), innerToken);
            }
            return null;
        }

        var match = findMatchingExpansion(innerToken);
        if (match != null) {
            String result = match.expansion().onContextRequest(context, match.params());
            if (result != null) {
                return result;
            }
        }

        // Fallback to PAPI external lookup if available
        return papiBridge.resolveExternal(context, innerToken);
    }

    private CompletionStage<@Nullable String> resolveTokenAsync(PlaceholderContext context, String innerToken) {
        if (innerToken.startsWith("rel_") || innerToken.startsWith("rel:")) {
            var match = findMatchingExpansion(innerToken.substring(4));
            if (match != null && match.expansion().supportsAsync()) {
                return invokeAsyncExpansion(match.expansion(), context, match.params());
            }
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Relational placeholder '" + innerToken
                                    + "' has no thread-safe asynchronous expansion; use synchronous parsing or an async UUID-based expansion"));
        }

        var match = findMatchingExpansion(innerToken);
        if (match != null) {
            if (match.expansion().supportsAsync()) {
                return invokeAsyncExpansion(match.expansion(), context, match.params());
            }
            return invokeSynchronousExpansionAsync(match.expansion(), context, match.params());
        }

        return invokeSynchronousValueAsync(context, () -> papiBridge.resolveExternal(context, innerToken));
    }

    private CompletionStage<@Nullable String> invokeAsyncExpansion(
            PlaceholderExpansion expansion, PlaceholderContext context, String params) {
        try {
            return Objects.requireNonNull(
                            expansion.onAsyncRequest(AsyncPlaceholderContext.from(context), params),
                            "Placeholder expansion returned a null CompletionStage")
                    .exceptionallyCompose(error -> {
                        logger.warning(
                                "Placeholder expansion '" + expansion.identifier() + "' failed: " + error.getMessage());
                        return CompletableFuture.failedFuture(error);
                    });
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<@Nullable String> invokeSynchronousExpansionAsync(
            PlaceholderExpansion expansion, PlaceholderContext context, String params) {
        return invokeSynchronousValueAsync(context, () -> expansion.onContextRequest(context, params));
    }

    private CompletionStage<@Nullable String> invokeSynchronousValueAsync(
            PlaceholderContext context, Supplier<@Nullable String> invocation) {
        var result = new CompletableFuture<@Nullable String>();
        Runnable task = () -> {
            try {
                result.complete(invocation.get());
            } catch (Exception failure) {
                result.completeExceptionally(failure);
            }
        };

        try {
            if (context.viewerId() != null) {
                scheduler.entity(context.viewerId(), task);
                return result;
            }
            if (context.targetId() != null) {
                scheduler.entity(context.targetId(), task);
                return result;
            }
            scheduler.global(task);
        } catch (RuntimeException schedulingFailure) {
            result.completeExceptionally(schedulingFailure);
        }
        return result;
    }

    private record ExpansionMatch(PlaceholderExpansion expansion, String params) {}

    private @Nullable ExpansionMatch findMatchingExpansion(String token) {
        // 1. Direct colon split (e.g. "prefix:params")
        int colonIndex = token.indexOf(':');
        if (colonIndex != -1) {
            String id = token.substring(0, colonIndex).toLowerCase(java.util.Locale.ROOT);
            PlaceholderExpansion exp = expansions.get(id);
            if (exp != null) {
                return new ExpansionMatch(exp, token.substring(colonIndex + 1));
            }
        }

        // 2. Exact match (e.g. "server_online" where full token is registered)
        PlaceholderExpansion exact = expansions.get(token.toLowerCase(java.util.Locale.ROOT));
        if (exact != null) {
            return new ExpansionMatch(exact, "");
        }

        // 3. Match longest registered prefix separated by '_' using pre-sorted descending key array
        String lowerToken = token.toLowerCase(java.util.Locale.ROOT);
        String[] sorted = sortedIdentifiers;
        for (String id : sorted) {
            if (lowerToken.startsWith(id + "_")) {
                PlaceholderExpansion exp = expansions.get(id);
                if (exp != null) {
                    String params = token.substring(id.length() + 1);
                    return new ExpansionMatch(exp, params);
                }
            }
        }

        return null;
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("PlaceholderService is already closed");
        }
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        if (closed.compareAndSet(false, true)) {
            papiBridge.unregister();
            expansions.clear();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        closeAsync().whenComplete((_, failure) -> {
            if (failure != null) {
                logger.warning("Failed to close placeholder service: " + failure.getMessage());
            }
        });
    }
}
