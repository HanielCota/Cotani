package com.cotani.staging;

import com.cotani.Cotani;
import com.cotani.task.CotaniTasks;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Minimal real-server harness for Paper/Folia lifecycle and scheduler smoke tests. */
public final class CotaniStagingPlugin extends JavaPlugin {

    private final AtomicLong heartbeatCount = new AtomicLong();
    private PaperTaskScheduler scheduler;
    private Cotani cotani;

    @Override
    public void onEnable() {
        scheduler = CotaniTasks.create(this);
        cotani = Cotani.forPlugin(this).with(scheduler).build();
        scheduler.asyncTimer(heartbeatCount::incrementAndGet, Duration.ofMillis(100), Duration.ofMillis(100));
        getLogger().info("Cotani staging enabled");
    }

    @Override
    public void onDisable() {
        var lifecycle = cotani;
        if (lifecycle != null) {
            var logger = getLogger();
            lifecycle.closeAsync().whenComplete((_, failure) -> {
                if (failure != null) {
                    logger.log(Level.SEVERE, "Cotani staging shutdown failed", failure);
                } else {
                    logger.info("Cotani staging shutdown complete");
                }
            });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Objects.requireNonNull(sender, "sender");
        sender.sendMessage("Cotani staging heartbeat=" + heartbeatCount.get());
        getLogger().info("Cotani staging command handled");
        return true;
    }
}
