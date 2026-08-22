package com.cotani.hud.impl;

import com.cotani.api.InternalApi;
import com.cotani.gui.api.Property;
import com.cotani.gui.api.Property.Subscription;
import com.cotani.hud.api.Sidebar;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link Sidebar}.
 */
@InternalApi
public final class DefaultSidebar implements Sidebar {

    private static final String PROPERTY_REQUIRED = "Parameter 'property' must not be null";
    private static final String MAPPER_REQUIRED = "Parameter 'mapper' must not be null";

    private final UUID playerId;
    private final ScoreboardRenderer renderer;
    private final Map<Integer, Supplier<Component>> lineSuppliers = new ConcurrentHashMap<>();
    private final Map<Integer, Subscription> lineSubscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final Object lock = new Object();
    private final @Nullable Consumer<UUID> onDestroy;

    private @Nullable Subscription titleSubscription;
    private Supplier<Component> titleSupplier;

    public DefaultSidebar(
            Player player,
            PaperTaskScheduler scheduler,
            Supplier<Component> initialTitleSupplier,
            Map<Integer, Supplier<Component>> initialLines,
            @Nullable Consumer<UUID> onDestroy) {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        this.playerId = player.getUniqueId();
        this.renderer = new ScoreboardRenderer(playerId, scheduler);
        this.titleSupplier =
                Objects.requireNonNull(initialTitleSupplier, "Parameter 'initialTitleSupplier' must not be null");
        this.lineSuppliers.putAll(initialLines);
        this.onDestroy = onDestroy;

        // Render initial content
        this.renderer.renderTitle(titleSupplier.get());
        for (var entry : lineSuppliers.entrySet()) {
            this.renderer.renderLine(entry.getKey(), entry.getValue().get());
        }
    }

    @Override
    public UUID viewerId() {
        return playerId;
    }

    @Override
    public Optional<Player> viewer() {
        var server = Bukkit.getServer();
        if (server == null) {
            return Optional.empty();
        }
        var player = server.getPlayer(playerId);
        return (player != null && player.isOnline()) ? Optional.of(player) : Optional.empty();
    }

    @Override
    public Sidebar title(Component title) {
        Objects.requireNonNull(title, "Parameter 'title' must not be null");
        synchronized (lock) {
            if (titleSubscription != null) {
                try {
                    titleSubscription.close();
                } catch (Exception _) {
                    // Suppress
                }
                titleSubscription = null;
            }
        }
        this.titleSupplier = () -> title;
        renderer.renderTitle(title);
        return this;
    }

    @Override
    public Sidebar line(int score, Component content) {
        Objects.requireNonNull(content, "Parameter 'content' must not be null");
        var oldSub = lineSubscriptions.remove(score);
        if (oldSub != null) {
            try {
                oldSub.close();
            } catch (Exception _) {
                // Suppress
            }
        }
        lineSuppliers.put(score, () -> content);
        renderer.renderLine(score, content);
        return this;
    }

    @Override
    public Sidebar removeLine(int score) {
        var oldSub = lineSubscriptions.remove(score);
        if (oldSub != null) {
            try {
                oldSub.close();
            } catch (Exception _) {
                // Suppress
            }
        }
        lineSuppliers.remove(score);
        renderer.removeLine(score);
        return this;
    }

    @Override
    public <T> Sidebar bindLine(int score, Property<T> property, Function<T, Component> mapper) {
        Objects.requireNonNull(property, PROPERTY_REQUIRED);
        Objects.requireNonNull(mapper, MAPPER_REQUIRED);

        var oldSub = lineSubscriptions.remove(score);
        if (oldSub != null) {
            try {
                oldSub.close();
            } catch (Exception _) {
                // Suppress
            }
        }

        var initial = mapper.apply(property.get());
        lineSuppliers.put(score, () -> initial);
        renderer.renderLine(score, initial);

        var sub = property.observe(newVal -> {
            if (!destroyed.get()) {
                var comp = mapper.apply(newVal);
                lineSuppliers.put(score, () -> comp);
                renderer.renderLine(score, comp);
            }
        });

        lineSubscriptions.put(score, sub);
        return this;
    }

    public <T> Sidebar bindTitle(Property<T> property, Function<T, Component> mapper) {
        Objects.requireNonNull(property, PROPERTY_REQUIRED);
        Objects.requireNonNull(mapper, MAPPER_REQUIRED);

        synchronized (lock) {
            if (titleSubscription != null) {
                try {
                    titleSubscription.close();
                } catch (Exception _) {
                    // Suppress
                }
            }

            var initial = mapper.apply(property.get());
            this.titleSupplier = () -> initial;
            renderer.renderTitle(initial);

            this.titleSubscription = property.observe(newVal -> {
                if (!destroyed.get()) {
                    var comp = mapper.apply(newVal);
                    titleSupplier = () -> comp;
                    renderer.renderTitle(comp);
                }
            });
        }

        return this;
    }

    @Override
    public void refresh() {
        if (destroyed.get()) {
            return;
        }

        renderer.renderTitle(titleSupplier.get());
        for (var entry : lineSuppliers.entrySet()) {
            renderer.renderLine(entry.getKey(), entry.getValue().get());
        }
    }

    @Override
    public boolean isDestroyed() {
        return destroyed.get();
    }

    @Override
    public void close() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }

        synchronized (lock) {
            if (titleSubscription != null) {
                try {
                    titleSubscription.close();
                } catch (Exception _) {
                    // Suppress
                }
                titleSubscription = null;
            }
        }

        for (var sub : lineSubscriptions.values()) {
            try {
                sub.close();
            } catch (Exception _) {
                // Suppress
            }
        }
        lineSubscriptions.clear();

        lineSuppliers.clear();
        renderer.close();

        if (onDestroy != null) {
            onDestroy.accept(playerId);
        }
    }
}
