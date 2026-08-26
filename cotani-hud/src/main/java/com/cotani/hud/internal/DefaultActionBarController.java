package com.cotani.hud.internal;

import com.cotani.api.InternalApi;
import com.cotani.gui.api.Property;
import com.cotani.gui.api.Property.Subscription;
import com.cotani.hud.api.ActionBarController;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link ActionBarController}.
 */
@InternalApi
public final class DefaultActionBarController implements ActionBarController {

    private static final String PLAYER_NULL_MSG = "Parameter 'player' must not be null";

    private final PaperTaskScheduler scheduler;
    private final Map<UUID, PlayerActionBarState> states = new ConcurrentHashMap<>();

    public DefaultActionBarController(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
    }

    @Override
    public void send(Player player, Component message) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        Objects.requireNonNull(message, "Parameter 'message' must not be null");

        scheduler.entity(player, () -> {
            if (player.isOnline()) {
                player.sendActionBar(message);
            }
        });
    }

    @Override
    public void sendTimed(Player player, Component message, Duration duration) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        Objects.requireNonNull(message, "Parameter 'message' must not be null");
        Objects.requireNonNull(duration, "Parameter 'duration' must not be null");

        var playerId = player.getUniqueId();
        var state = states.computeIfAbsent(playerId, _ -> new PlayerActionBarState());
        state.cleanup();

        send(player, message);

        var totalMillis = duration.toMillis();
        var startTime = System.currentTimeMillis();

        var task = scheduler.asyncTimer(
                () -> {
                    var elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed >= totalMillis) {
                        state.cancelTimedTask();
                        return;
                    }
                    // The player is resolved inside the entity-thread hop: the timer body runs on an
                    // async scheduler thread and must not touch Bukkit lookups off the owning thread.
                    scheduler.entity(playerId, () -> {
                        var current = Bukkit.getPlayer(playerId);
                        if (current == null || !current.isOnline()) {
                            state.cancelTimedTask();
                            return;
                        }
                        current.sendActionBar(message);
                    });
                },
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));

        state.setTimedTask(task);
    }

    @Override
    public <T> Property.Subscription bind(Player player, Property<T> property, Function<T, Component> mapper) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        Objects.requireNonNull(property, "Parameter 'property' must not be null");
        Objects.requireNonNull(mapper, "Parameter 'mapper' must not be null");

        var state = states.computeIfAbsent(player.getUniqueId(), _ -> new PlayerActionBarState());
        state.cleanup();

        send(player, mapper.apply(property.get()));

        // The observer only forwards the mapped value; player liveness and delivery are resolved on
        // the owning entity thread inside {@link #send}.
        var sub = property.observe(newVal -> send(player, mapper.apply(newVal)));

        state.setSubscription(sub);

        return () -> {
            sub.close();
            state.removeSubscription(sub);
        };
    }

    @Override
    public void clear(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        var removed = states.remove(player.getUniqueId());
        if (removed != null) {
            removed.cleanup();
        }

        scheduler.entity(player, () -> {
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        });
    }

    /**
     * Clears all action bars and cancels all timers.
     */
    public void close() {
        states.values().forEach(PlayerActionBarState::cleanup);
        states.clear();
    }

    private static final class PlayerActionBarState {
        private @Nullable SchedulerTask timedTask;
        private @Nullable Subscription subscription;

        synchronized void setTimedTask(SchedulerTask task) {
            cancelTimedTask();
            this.timedTask = task;
        }

        synchronized void cancelTimedTask() {
            if (timedTask != null) {
                timedTask.cancel();
                timedTask = null;
            }
        }

        synchronized void setSubscription(Property.Subscription subscription) {
            clearSubscription();
            this.subscription = subscription;
        }

        synchronized void clearSubscription() {
            if (subscription != null) {
                try {
                    subscription.close();
                } catch (Exception exception) {
                    java.util.logging.Logger.getLogger(DefaultActionBarController.class.getName())
                            .log(java.util.logging.Level.FINE, "Could not close action bar subscription", exception);
                }
                subscription = null;
            }
        }

        synchronized void removeSubscription(Property.Subscription sub) {
            if (Objects.equals(this.subscription, sub)) {
                this.subscription = null;
            }
        }

        synchronized void cleanup() {
            cancelTimedTask();
            clearSubscription();
        }
    }
}
