package com.example.cotaniquickstart;

import com.cotani.Cotani;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.scheduler.SchedulerFactory;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class CotaniQuickStartPlugin extends JavaPlugin {
    private @Nullable Cotani lifecycle;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        lifecycle = Cotani.forPlugin(this).withAsync(scheduler::closeAsync).build();

        var command = getCommand("cotanihello");

        if (command == null) {
            throw new IllegalStateException("Command 'cotanihello' is missing from plugin.yml");
        }

        command.setExecutor(new HelloCommand(new HelloService(scheduler)));
    }

    @Override
    public void onDisable() {
        var currentLifecycle = lifecycle;

        if (currentLifecycle == null) {
            return;
        }

        var _ = currentLifecycle.closeAsync().whenComplete((_, failure) -> {
            if (failure != null) {
                getLogger().log(Level.SEVERE, "Could not close Cotani resources", failure);
            }
        });
    }

    private record HelloCommand(HelloService helloService) implements CommandExecutor {
        private HelloCommand(HelloService helloService) {
            this.helloService = Objects.requireNonNull(helloService, "helloService");
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            helloService.greet(player.getUniqueId());

            return true;
        }
    }

    private record HelloService(PaperTaskScheduler scheduler) {
        private HelloService(PaperTaskScheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        }

        private void greet(UUID playerId) {
            Objects.requireNonNull(playerId, "playerId");

            scheduler.entity("cotani-hello", playerId, () -> {
                var player = Bukkit.getPlayer(playerId);

                if (player != null) {
                    player.sendMessage(Component.text("Cotani is running on your entity thread."));
                }
            });
        }
    }
}
