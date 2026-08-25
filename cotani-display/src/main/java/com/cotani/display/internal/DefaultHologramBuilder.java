package com.cotani.display.internal;

import com.cotani.api.InternalApi;
import com.cotani.display.api.BlockLine;
import com.cotani.display.api.DisplayBillboard;
import com.cotani.display.api.Hologram;
import com.cotani.display.api.HologramBuilder;
import com.cotani.display.api.HologramClickHandler;
import com.cotani.display.api.HologramLine;
import com.cotani.display.api.HologramService;
import com.cotani.display.api.ItemLine;
import com.cotani.display.api.TextLine;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.text.MiniMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultHologramBuilder implements HologramBuilder {

    private final HologramService service;
    private final PaperTaskScheduler scheduler;
    private @Nullable String name;
    private final List<HologramLine> lines = new ArrayList<>();
    private DisplayBillboard defaultBillboard = DisplayBillboard.CENTER;
    private double spacing = TextLine.DEFAULT_HEIGHT_OFFSET;
    private boolean clickable;
    private @Nullable HologramClickHandler clickHandler;

    public DefaultHologramBuilder(HologramService service, PaperTaskScheduler scheduler) {
        this.service = Objects.requireNonNull(service, "service cannot be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
    }

    public DefaultHologramBuilder(HologramService service, PaperTaskScheduler scheduler, String name) {
        this.service = Objects.requireNonNull(service, "service cannot be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    @Override
    public HologramBuilder name(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        return this;
    }

    @Override
    public HologramBuilder addLine(HologramLine line) {
        Objects.requireNonNull(line, "line cannot be null");
        lines.add(line);
        return this;
    }

    @Override
    public HologramBuilder addLine(Component component) {
        Objects.requireNonNull(component, "component cannot be null");
        return addLine(TextLine.of(component, defaultBillboard, 1.0f).withHeightOffset(spacing));
    }

    @Override
    public HologramBuilder addLine(String miniMessageText) {
        Objects.requireNonNull(miniMessageText, "miniMessageText cannot be null");
        var component = MiniMessages.parse(miniMessageText);
        return addLine(TextLine.of(component, defaultBillboard, 1.0f).withHeightOffset(spacing));
    }

    @Override
    public HologramBuilder addItemLine(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        return addLine(ItemLine.of(item).withBillboard(defaultBillboard).withHeightOffset(spacing));
    }

    @Override
    public HologramBuilder addItemLine(ItemStack item, float scale) {
        Objects.requireNonNull(item, "item cannot be null");
        return addLine(ItemLine.of(item, scale).withBillboard(defaultBillboard).withHeightOffset(spacing));
    }

    @Override
    public HologramBuilder addBlockLine(BlockData blockData) {
        Objects.requireNonNull(blockData, "blockData cannot be null");
        return addLine(BlockLine.of(blockData, defaultBillboard, 0.5f).withHeightOffset(spacing));
    }

    @Override
    public HologramBuilder billboard(DisplayBillboard billboard) {
        this.defaultBillboard = Objects.requireNonNull(billboard, "billboard cannot be null");
        return this;
    }

    @Override
    public HologramBuilder lineSpacing(double spacing) {
        this.spacing = spacing;
        return this;
    }

    @Override
    public HologramBuilder clickable(boolean clickable) {
        this.clickable = clickable;
        return this;
    }

    @Override
    public HologramBuilder onClick(HologramClickHandler handler) {
        this.clickHandler = Objects.requireNonNull(handler, "handler cannot be null");
        this.clickable = true;
        return this;
    }

    @Override
    public Hologram build() {
        var defaultService = service instanceof DefaultHologramService ds ? ds : null;
        var hologram = new DefaultHologram(
                UUID.randomUUID(), name, lines, spacing, clickable, clickHandler, scheduler, defaultService);
        if (defaultService != null) {
            defaultService.register(hologram);
        }
        return hologram;
    }

    @Override
    public CompletionStage<Hologram> spawnAsync(Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        var hologram = build();
        return hologram.spawnAsync(location);
    }
}
