package com.cotani.hud.impl;

import com.cotani.api.InternalApi;
import com.cotani.gui.api.Property;
import com.cotani.gui.api.Property.Subscription;
import com.cotani.hud.api.HudBossBar;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link HudBossBar}.
 */
@InternalApi
public final class DefaultHudBossBar implements HudBossBar {

    private static final String PLAYER_NULL_MSG = "Parameter 'player' must not be null";

    private final BossBar bar;
    private final PaperTaskScheduler scheduler;
    private final Set<UUID> viewerIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lock = new Object();
    private final @Nullable Consumer<HudBossBar> onDestroy;

    private @Nullable SchedulerTask countdownTask;
    private @Nullable Subscription progressSubscription;
    private @Nullable Subscription titleSubscription;

    public DefaultHudBossBar(
            BossBar bar,
            PaperTaskScheduler scheduler,
            @Nullable Duration countdownDuration,
            @Nullable Consumer<HudBossBar> onDestroy) {
        this.bar = Objects.requireNonNull(bar, "Parameter 'bar' must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
        this.onDestroy = onDestroy;

        if (countdownDuration != null && !countdownDuration.isNegative() && !countdownDuration.isZero()) {
            startCountdown(countdownDuration);
        }
    }

    private void startCountdown(Duration duration) {
        var totalMillis = duration.toMillis();
        var startTime = System.currentTimeMillis();

        this.countdownTask = scheduler.asyncTimer(
                () -> {
                    if (closed.get()) {
                        return;
                    }
                    var elapsed = System.currentTimeMillis() - startTime;
                    var remaining = totalMillis - elapsed;
                    if (remaining <= 0) {
                        dispatchBarUpdate(current -> current.progress(0.0f));
                        close();
                        return;
                    }
                    var progress = Math.clamp((float) remaining / (float) totalMillis, 0.0f, 1.0f);
                    dispatchBarUpdate(current -> current.progress(progress));
                },
                Duration.ZERO,
                Duration.ofMillis(50));
    }

    @Override
    public BossBar adventureBar() {
        return bar;
    }

    @Override
    public HudBossBar title(Component title) {
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
        dispatchBarUpdate(current -> current.name(title));
        return this;
    }

    @Override
    public HudBossBar progress(float progress) {
        synchronized (lock) {
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
            if (progressSubscription != null) {
                try {
                    progressSubscription.close();
                } catch (Exception _) {
                    // Suppress
                }
                progressSubscription = null;
            }
        }
        float clampedProgress = Math.clamp(progress, 0.0f, 1.0f);
        dispatchBarUpdate(current -> current.progress(clampedProgress));
        return this;
    }

    @Override
    public HudBossBar color(BossBar.Color color) {
        Objects.requireNonNull(color, "Parameter 'color' must not be null");
        dispatchBarUpdate(current -> current.color(color));
        return this;
    }

    @Override
    public HudBossBar overlay(BossBar.Overlay overlay) {
        Objects.requireNonNull(overlay, "Parameter 'overlay' must not be null");
        dispatchBarUpdate(current -> current.overlay(overlay));
        return this;
    }

    @Override
    public HudBossBar show(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        if (closed.get()) {
            return this;
        }

        UUID viewerId = player.getUniqueId();
        viewerIds.add(viewerId);
        scheduler.entity(viewerId, () -> {
            var current = Bukkit.getPlayer(viewerId);
            if (current != null && current.isOnline()) {
                current.showBossBar(bar);
            }
        });

        return this;
    }

    @Override
    public HudBossBar hide(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        UUID viewerId = player.getUniqueId();
        viewerIds.remove(viewerId);

        scheduler.entity(viewerId, () -> {
            var current = Bukkit.getPlayer(viewerId);
            if (current != null && current.isOnline()) {
                current.hideBossBar(bar);
            }
        });

        return this;
    }

    @Override
    public Set<Player> viewers() {
        var server = Bukkit.getServer();
        if (server == null) {
            return Collections.emptySet();
        }

        return viewerIds.stream()
                .map(server::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<UUID> viewerIds() {
        return Set.copyOf(viewerIds);
    }

    @Override
    public HudBossBar bindProgress(Property<Float> property) {
        Objects.requireNonNull(property, "Parameter 'property' must not be null");

        synchronized (lock) {
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
            if (progressSubscription != null) {
                try {
                    progressSubscription.close();
                } catch (Exception _) {
                    // Suppress
                }
            }

            Float initialVal = property.get();
            float initialProgress = initialVal == null ? 0.0f : Math.clamp(initialVal, 0.0f, 1.0f);
            dispatchBarUpdate(current -> current.progress(initialProgress));

            this.progressSubscription = property.observe(newVal -> {
                if (!closed.get()) {
                    float nextProgress = newVal == null ? 0.0f : Math.clamp(newVal, 0.0f, 1.0f);
                    dispatchBarUpdate(current -> current.progress(nextProgress));
                }
            });
        }

        return this;
    }

    @Override
    public <T> HudBossBar bindTitle(Property<T> property, Function<T, Component> mapper) {
        Objects.requireNonNull(property, "Parameter 'property' must not be null");
        Objects.requireNonNull(mapper, "Parameter 'mapper' must not be null");

        synchronized (lock) {
            if (titleSubscription != null) {
                try {
                    titleSubscription.close();
                } catch (Exception _) {
                    // Suppress
                }
            }

            Component initialTitle = mapper.apply(property.get());
            dispatchBarUpdate(current -> current.name(initialTitle));

            this.titleSubscription = property.observe(newVal -> {
                if (!closed.get()) {
                    Component nextTitle = mapper.apply(newVal);
                    dispatchBarUpdate(current -> current.name(nextTitle));
                }
            });
        }

        return this;
    }

    @Override
    public boolean isDestroyed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        synchronized (lock) {
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
            if (progressSubscription != null) {
                try {
                    progressSubscription.close();
                } catch (Exception _) {
                    // Suppress
                }
                progressSubscription = null;
            }
            if (titleSubscription != null) {
                try {
                    titleSubscription.close();
                } catch (Exception _) {
                    // Suppress
                }
                titleSubscription = null;
            }
        }

        for (var uuid : viewerIds) {
            scheduler.entity(uuid, () -> {
                var current = Bukkit.getPlayer(uuid);
                if (current != null && current.isOnline()) {
                    current.hideBossBar(bar);
                }
            });
        }
        viewerIds.clear();

        if (onDestroy != null) {
            onDestroy.accept(this);
        }
    }

    private void dispatchBarUpdate(Consumer<BossBar> update) {
        Objects.requireNonNull(update, "Parameter 'update' must not be null");
        if (closed.get()) {
            return;
        }
        scheduler.global("hud-bossbar-update", () -> {
            if (!closed.get()) {
                update.accept(bar);
            }
        });
    }
}
