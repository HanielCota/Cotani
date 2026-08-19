package com.cotani.examples.showcase;

import com.cotani.teleport.api.PendingTeleportService;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportOptions;
import com.cotani.text.AudienceMessages;
import com.cotani.text.Placeholders;
import java.time.Duration;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ShowcaseTeleportCommand implements CommandExecutor {
    private final PendingTeleportService pendingTeleportService;

    public ShowcaseTeleportCommand(PendingTeleportService pendingTeleportService) {
        this.pendingTeleportService = Objects.requireNonNull(pendingTeleportService, "pendingTeleportService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem executar este comando.");
            return true;
        }

        var spawnLocation = player.getWorld().getSpawnLocation();
        var options =
                TeleportOptions.builder().checkCombat(true).safeLocation(true).build();

        pendingTeleportService.schedule(
                player.getUniqueId(),
                spawnLocation,
                Duration.ofSeconds(3),
                options,
                TeleportCause.SPAWN,
                "spawn_command");

        AudienceMessages.sendMessage(
                player,
                "<gold>Teleportando para o spawn em <yellow><seconds>s</yellow>. Não se mova nem receba dano!",
                Placeholders.unparsed("seconds", "3"));
        return true;
    }
}
