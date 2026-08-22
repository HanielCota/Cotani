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
                        bar.progress(0.0f);
                        close();
                    } else {
                        var progress = Math.clamp((float) remaining / (float) totalMillis, 0.0f, 1.0f);
                        bar.progress(progress);
                    }
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
        bar.name(title);
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
        bar.progress(Math.clamp(progress, 0.0f, 1.0f));
        return this;
    }

    @Override
    public HudBossBar color(BossBar.Color color) {
        Objects.requireNonNull(color, "Parameter 'color' must not be null");
        bar.color(color);
        return this;
    }

    @Override
    public HudBossBar overlay(BossBar.Overlay overlay) {
        Objects.requireNonNull(overlay, "Parameter 'overlay' must not be null");
        bar.overlay(overlay);
        return this;
    }

    @Override
    public HudBossBar show(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        if (closed.get()) {
            return this;
        }

        viewerIds.add(player.getUniqueId());
        scheduler.entity(player, () -> {
            if (player.isOnline()) {
                player.showBossBar(bar);
            }
        });

        return this;
    }

    @Override
    public HudBossBar hide(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        viewerIds.remove(player.getUniqueId());

        scheduler.entity(player, () -> {
            if (player.isOnline()) {
                player.hideBossBar(bar);
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

            bar.progress(Math.clamp(property.get(), 0.0f, 1.0f));

            this.progressSubscription = property.observe(newVal -> {
                if (!closed.get()) {
                    bar.progress(Math.clamp(newVal, 0.0f, 1.0f));
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

            bar.name(mapper.apply(property.get()));

            this.titleSubscription = property.observe(newVal -> {
                if (!closed.get()) {
                    bar.name(mapper.apply(newVal));
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

        var server = Bukkit.getServer();
        if (server != null) {
            for (var uuid : viewerIds) {
                var p = server.getPlayer(uuid);
                if (p != null) {
                    scheduler.entity(p, () -> {
                        if (p.isOnline()) {
                            p.hideBossBar(bar);
                        }
                    });
                }
            }
        }
        viewerIds.clear();

        if (onDestroy != null) {
            onDestroy.accept(this);
        }
    }
}
