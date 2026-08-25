package com.cotani.hud.impl;

import com.cotani.api.InternalApi;
import com.cotani.gui.api.Property;
import com.cotani.gui.api.Property.Subscription;
import com.cotani.hud.api.TabListManager;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link TabListManager}.
 */
@InternalApi
public final class DefaultTabListManager implements TabListManager {

    private static final String PLAYER_NULL_MSG = "Parameter 'player' must not be null";

    private final PaperTaskScheduler scheduler;
    private final Map<UUID, PlayerTabListState> states = new ConcurrentHashMap<>();

    public DefaultTabListManager(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
    }

    @Override
    public void setHeader(Player player, Component header) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        Objects.requireNonNull(header, "Parameter 'header' must not be null");

        var state = states.computeIfAbsent(player.getUniqueId(), _ -> new PlayerTabListState());
        state.clearHeaderSubscription();
        state.header.set(header);
        render(player, state);
    }

    @Override
    public void setFooter(Player player, Component footer) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        Objects.requireNonNull(footer, "Parameter 'footer' must not be null");

        var state = states.computeIfAbsent(player.getUniqueId(), _ -> new PlayerTabListState());
        state.clearFooterSubscription();
        state.footer.set(footer);
        render(player, state);
    }

    @Override
    public void setHeaderAndFooter(Player player, Component header, Component footer) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        Objects.requireNonNull(header, "Parameter 'header' must not be null");
        Objects.requireNonNull(footer, "Parameter 'footer' must not be null");

        var state = states.computeIfAbsent(player.getUniqueId(), _ -> new PlayerTabListState());
        state.clearHeaderSubscription();
        state.clearFooterSubscription();
        state.header.set(header);
        state.footer.set(footer);
        render(player, state);
    }

    @Override
    public <T> Property.Subscription bindHeader(Player player, Property<T> property, Function<T, Component> mapper) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        Objects.requireNonNull(property, "Parameter 'property' must not be null");
        Objects.requireNonNull(mapper, "Parameter 'mapper' must not be null");

        var state = states.computeIfAbsent(player.getUniqueId(), _ -> new PlayerTabListState());
        state.clearHeaderSubscription();

        state.header.set(mapper.apply(property.get()));
        render(player, state);

        var playerId = player.getUniqueId();
        var sub = property.observe(newVal -> {
            var p = Bukkit.getServer() != null ? Bukkit.getPlayer(playerId) : player;
            if (p != null && p.isOnline()) {
                state.header.set(mapper.apply(newVal));
                render(p, state);
            }
        });

        state.setHeaderSubscription(sub);

        return () -> {
            sub.close();
            state.removeHeaderSubscription(sub);
        };
    }

    @Override
    public <T> Property.Subscription bindFooter(Player player, Property<T> property, Function<T, Component> mapper) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        Objects.requireNonNull(property, "Parameter 'property' must not be null");
        Objects.requireNonNull(mapper, "Parameter 'mapper' must not be null");

        var state = states.computeIfAbsent(player.getUniqueId(), _ -> new PlayerTabListState());
        state.clearFooterSubscription();

        var initial = mapper.apply(property.get());
        state.footer.set(initial);
        render(player, state);

        var playerId = player.getUniqueId();
        var sub = property.observe(newVal -> {
            var p = Bukkit.getServer() != null ? Bukkit.getPlayer(playerId) : player;
            if (p != null && p.isOnline()) {
                state.footer.set(mapper.apply(newVal));
                render(p, state);
            }
        });

        state.setFooterSubscription(sub);

        return () -> {
            sub.close();
            state.removeFooterSubscription(sub);
        };
    }

    public void close() {
        for (var state : states.values()) {
            state.closeSubscriptions();
        }
        states.clear();
    }

    @Override
    public void clear(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        var removed = states.remove(player.getUniqueId());
        if (removed != null) {
            removed.closeSubscriptions();
        }

        scheduler.entity(player, () -> {
            if (player.isOnline()) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
        });
    }

    private void render(Player player, PlayerTabListState state) {
        var header = Objects.requireNonNullElse(state.header.get(), Component.empty());
        var footer = Objects.requireNonNullElse(state.footer.get(), Component.empty());
        scheduler.entity(player, () -> {
            if (player.isOnline()) {
                player.sendPlayerListHeaderAndFooter(header, footer);
            }
        });
    }

    private static final class PlayerTabListState {
        private @Nullable Subscription headerSubscription;
        private @Nullable Subscription footerSubscription;
        private final AtomicReference<Component> header = new AtomicReference<>(Component.empty());
        private final AtomicReference<Component> footer = new AtomicReference<>(Component.empty());

        synchronized void setHeaderSubscription(Subscription subscription) {
            this.headerSubscription = subscription;
        }

        synchronized void clearHeaderSubscription() {
            if (headerSubscription != null) {
                try {
                    headerSubscription.close();
                } catch (Exception exception) {
                    java.util.logging.Logger.getLogger(DefaultTabListManager.class.getName())
                            .log(java.util.logging.Level.FINE, "Could not close tab list subscription", exception);
                }
                headerSubscription = null;
            }
        }

        synchronized void removeHeaderSubscription(Subscription subscription) {
            if (Objects.equals(this.headerSubscription, subscription)) {
                this.headerSubscription = null;
            }
        }

        synchronized void setFooterSubscription(Subscription subscription) {
            this.footerSubscription = subscription;
        }

        synchronized void clearFooterSubscription() {
            if (footerSubscription != null) {
                try {
                    footerSubscription.close();
                } catch (Exception exception) {
                    java.util.logging.Logger.getLogger(DefaultTabListManager.class.getName())
                            .log(java.util.logging.Level.FINE, "Could not close tab list subscription", exception);
                }
                footerSubscription = null;
            }
        }

        synchronized void removeFooterSubscription(Subscription subscription) {
            if (Objects.equals(this.footerSubscription, subscription)) {
                this.footerSubscription = null;
            }
        }

        synchronized void closeSubscriptions() {
            clearHeaderSubscription();
            clearFooterSubscription();
        }
    }
}
