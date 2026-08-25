package com.cotani.examples;

import com.cotani.Cotani;
import com.cotani.config.CotaniConfigs;
import com.cotani.economy.EconomyService;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.event.api.EventBus;
import com.cotani.event.bus.DefaultEventBus;
import com.cotani.event.exception.LoggingEventExceptionHandler;
import com.cotani.metrics.CotaniMetrics;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.util.VoidResult;
import com.cotani.teleport.api.TeleportRequest;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.api.TeleportService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.cotani.metrics.CotaniMetricsModule;
import net.cotani.metrics.config.MetricsConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Compile-checked counterparts for the most frequently copied documentation snippets. */
public final class CotaniExamples {
    private CotaniExamples() {}

    public static Cotani lifecycle(Plugin plugin, PaperTaskScheduler scheduler) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        return Cotani.forPlugin(plugin).withAsync(scheduler::closeAsync).build();
    }

    public static CompletionStage<CotaniConfigs> loadConfigAsync(Plugin plugin, PaperTaskScheduler scheduler) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        return CotaniConfigs.builder(plugin, scheduler).file("config.yml").loadAsync();
    }

    public static CompletionStage<EconomyTransaction> rewardAsync(
            EconomyService economy, UUID userId, BigDecimal amount) {
        java.util.Objects.requireNonNull(economy, "economy");
        java.util.Objects.requireNonNull(userId, "userId");
        java.util.Objects.requireNonNull(amount, "amount");
        return economy.depositAsync(
                userId, amount, EconomyReason.system("example.reward"), EconomyOperationId.random());
    }

    public static CompletionStage<TeleportResult> teleportAsync(TeleportService teleports, TeleportRequest request) {
        java.util.Objects.requireNonNull(teleports, "teleports");
        java.util.Objects.requireNonNull(request, "request");
        return teleports.teleportAsync(request);
    }

    public static EventBus eventBus(PaperTaskScheduler scheduler) {
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        return DefaultEventBus.create(LoggingEventExceptionHandler.usingJavaLogger(), scheduler.asyncExecutor());
    }

    public static CotaniMetricsModule metrics(MetricsConfig config) {
        java.util.Objects.requireNonNull(config, "config");
        return CotaniMetrics.create(config);
    }

    public static CompletionStage<Void> messagePlayerAsync(
            CompletionStage<String> messageStage, UUID playerId, PaperTaskScheduler scheduler) {
        java.util.Objects.requireNonNull(messageStage, "messageStage");
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        return scheduler
                .chain(messageStage)
                .consumeEntity(playerId, message -> {
                    var player = Bukkit.getPlayer(playerId);

                    if (player != null) {
                        player.sendMessage(Component.text(message));
                    }
                })
                .toCompletionStage()
                .thenApply(_ -> VoidResult.nullValue());
    }

    public static com.cotani.command.CotaniCommands commands(Plugin plugin, PaperTaskScheduler scheduler) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        return com.cotani.command.CotaniCommands.create(plugin, scheduler);
    }
}
