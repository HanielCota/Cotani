package com.cotani.hud.impl;

import com.cotani.api.InternalApi;
import com.cotani.gui.api.Property;
import com.cotani.hud.api.Sidebar;
import com.cotani.hud.api.SidebarBuilder;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link SidebarBuilder}.
 */
@InternalApi
public final class DefaultSidebarBuilder implements SidebarBuilder {

    private final PaperTaskScheduler scheduler;
    private final @Nullable Consumer<Sidebar> onRegister;
    private final @Nullable Consumer<UUID> onDestroy;

    private Supplier<Component> titleSupplier = Component::empty;
    private @Nullable PropertyBinding<?> titleBinding;
    private final Map<Integer, Function<Player, Component>> lineProviders = new HashMap<>();
    private final Map<Integer, PropertyBinding<?>> lineBindings = new HashMap<>();

    public DefaultSidebarBuilder(
            PaperTaskScheduler scheduler, @Nullable Consumer<Sidebar> onRegister, @Nullable Consumer<UUID> onDestroy) {
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
        this.onRegister = onRegister;
        this.onDestroy = onDestroy;
    }

    @Override
    public SidebarBuilder title(Component title) {
        Objects.requireNonNull(title, "Parameter 'title' must not be null");
        this.titleSupplier = () -> title;
        this.titleBinding = null;
        return this;
    }

    @Override
    public SidebarBuilder title(Supplier<Component> supplier) {
        Objects.requireNonNull(supplier, "Parameter 'supplier' must not be null");
        this.titleSupplier = supplier;
        this.titleBinding = null;
        return this;
    }

    @Override
    public <T> SidebarBuilder bindTitle(Property<T> property, Function<T, Component> mapper) {
        Objects.requireNonNull(property, "Parameter 'property' must not be null");
        Objects.requireNonNull(mapper, "Parameter 'mapper' must not be null");
        this.titleBinding = new PropertyBinding<>(property, mapper);
        this.titleSupplier = () -> mapper.apply(property.get());
        return this;
    }

    @Override
    public SidebarBuilder line(int score, Component content) {
        validateScore(score);
        Objects.requireNonNull(content, "Parameter 'content' must not be null");
        lineProviders.put(score, _ -> content);
        lineBindings.remove(score);
        return this;
    }

    @Override
    public SidebarBuilder line(int score, Function<Player, Component> provider) {
        validateScore(score);
        Objects.requireNonNull(provider, "Parameter 'provider' must not be null");
        lineProviders.put(score, provider);
        lineBindings.remove(score);
        return this;
    }

    @Override
    public SidebarBuilder line(int score, Supplier<Component> supplier) {
        validateScore(score);
        Objects.requireNonNull(supplier, "Parameter 'supplier' must not be null");
        lineProviders.put(score, _ -> supplier.get());
        lineBindings.remove(score);
        return this;
    }

    @Override
    public <T> SidebarBuilder bindLine(int score, Property<T> property, Function<T, Component> mapper) {
        validateScore(score);
        Objects.requireNonNull(property, "Parameter 'property' must not be null");
        Objects.requireNonNull(mapper, "Parameter 'mapper' must not be null");
        lineBindings.put(score, new PropertyBinding<>(property, mapper));
        lineProviders.remove(score);
        return this;
    }

    private static void validateScore(int score) {
        if (score < 1 || score > 15) {
            throw new IllegalArgumentException("Score index must be between 1 and 15, inclusive: " + score);
        }
    }

    @Override
    public Sidebar apply(Player player) {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");

        Map<Integer, Supplier<Component>> lines = new HashMap<>();
        for (var entry : lineProviders.entrySet()) {
            var fn = entry.getValue();
            lines.put(entry.getKey(), () -> fn.apply(player));
        }

        var sidebar = new DefaultSidebar(player, scheduler, titleSupplier, lines, onDestroy);

        if (titleBinding != null) {
            titleBinding.applyToTitle(sidebar);
        }

        for (var entry : lineBindings.entrySet()) {
            entry.getValue().applyToLine(sidebar, entry.getKey());
        }

        if (onRegister != null) {
            onRegister.accept(sidebar);
        }

        return sidebar;
    }

    private record PropertyBinding<T>(Property<T> property, Function<T, Component> mapper) {
        void applyToTitle(DefaultSidebar sidebar) {
            sidebar.bindTitle(property, mapper);
        }

        void applyToLine(DefaultSidebar sidebar, int score) {
            sidebar.bindLine(score, property, mapper);
        }
    }
}
