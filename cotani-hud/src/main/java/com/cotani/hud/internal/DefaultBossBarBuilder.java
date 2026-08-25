package com.cotani.hud.internal;

import com.cotani.api.InternalApi;
import com.cotani.hud.api.BossBarBuilder;
import com.cotani.hud.api.HudBossBar;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link BossBarBuilder}.
 */
@InternalApi
public final class DefaultBossBarBuilder implements BossBarBuilder {

    private final PaperTaskScheduler scheduler;
    private final @Nullable Consumer<HudBossBar> onRegister;
    private final @Nullable Consumer<HudBossBar> onDestroy;

    private Component title = Component.empty();
    private BossBar.Color color = BossBar.Color.WHITE;
    private BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
    private float progress = 1.0f;
    private Set<BossBar.Flag> flags = Collections.emptySet();
    private @Nullable Duration countdownDuration;

    public DefaultBossBarBuilder(
            PaperTaskScheduler scheduler,
            @Nullable Consumer<HudBossBar> onRegister,
            @Nullable Consumer<HudBossBar> onDestroy) {
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
        this.onRegister = onRegister;
        this.onDestroy = onDestroy;
    }

    @Override
    public BossBarBuilder title(Component title) {
        this.title = Objects.requireNonNull(title, "Parameter 'title' must not be null");
        return this;
    }

    @Override
    public BossBarBuilder color(BossBar.Color color) {
        this.color = Objects.requireNonNull(color, "Parameter 'color' must not be null");
        return this;
    }

    @Override
    public BossBarBuilder overlay(BossBar.Overlay overlay) {
        this.overlay = Objects.requireNonNull(overlay, "Parameter 'overlay' must not be null");
        return this;
    }

    @Override
    public BossBarBuilder progress(float progress) {
        this.progress = Math.clamp(progress, 0.0f, 1.0f);
        return this;
    }

    @Override
    public BossBarBuilder flags(Set<BossBar.Flag> flags) {
        this.flags = Set.copyOf(Objects.requireNonNull(flags, "Parameter 'flags' must not be null"));
        return this;
    }

    @Override
    public BossBarBuilder countdown(Duration duration) {
        this.countdownDuration = Objects.requireNonNull(duration, "Parameter 'duration' must not be null");
        return this;
    }

    @Override
    public HudBossBar build() {
        var bar = BossBar.bossBar(title, progress, color, overlay, flags);
        var hudBar = new DefaultHudBossBar(bar, scheduler, countdownDuration, onDestroy);

        if (onRegister != null) {
            onRegister.accept(hudBar);
        }

        return hudBar;
    }

    @Override
    public HudBossBar show(Player player) {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        var hudBar = build();
        hudBar.show(player);
        return hudBar;
    }
}
