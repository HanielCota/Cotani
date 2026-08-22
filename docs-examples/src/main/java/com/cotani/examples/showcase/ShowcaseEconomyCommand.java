package com.cotani.examples.showcase;

import com.cotani.economy.EconomyService;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.text.AudienceMessages;
import com.cotani.text.Placeholders;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ShowcaseEconomyCommand implements CommandExecutor {
    private final EconomyService economyService;
    private final PaperTaskScheduler scheduler;

    public ShowcaseEconomyCommand(EconomyService economyService, PaperTaskScheduler scheduler) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem executar este comando.");
            return true;
        }

        if (args.length == 0) {
            showBalance(player.getUniqueId());
            return true;
        }

        if ("daily".equalsIgnoreCase(args[0])) {
            claimDailyReward(player.getUniqueId());
            return true;
        }

        AudienceMessages.sendMessage(
                player, "<red>Uso correto: <yellow>/<label> [daily]</yellow>", Placeholders.unparsed("label", label));
        return true;
    }

    private void showBalance(UUID playerId) {
        var _ = scheduler
                .chain(economyService.balance(playerId))
                .consumeEntity(playerId, balance -> {
                    var onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer == null) {
                        return;
                    }
                    AudienceMessages.sendMessage(
                            onlinePlayer,
                            "<green>Seu saldo atual é: <gold><balance></gold> <yellow><symbol></yellow>",
                            Placeholders.unparsed("balance", balance.amount().toPlainString()),
                            Placeholders.unparsed("symbol", balance.currencyId().value()));
                })
                .onError(_ -> scheduler.entity(playerId, () -> {
                    var onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer != null) {
                        AudienceMessages.sendMessage(
                                onlinePlayer, "<red>Erro ao consultar seu saldo. Tente novamente mais tarde.");
                    }
                }))
                .toCompletionStage();
    }

    private void claimDailyReward(UUID playerId) {
        var rewardAmount = BigDecimal.valueOf(100);
        var operationId = EconomyOperationId.random();
        var reason = EconomyReason.system("daily_reward");

        var _ = scheduler
                .chain(economyService.deposit(playerId, rewardAmount, reason, operationId))
                .consumeEntity(playerId, tx -> {
                    var onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer == null) {
                        return;
                    }
                    AudienceMessages.sendMessage(
                            onlinePlayer,
                            "<green>Você resgatou com sucesso <gold><amount></gold> moedas!",
                            Placeholders.unparsed("amount", tx.amount().toPlainString()));
                })
                .onError(_ -> scheduler.entity(playerId, () -> {
                    var onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer != null) {
                        AudienceMessages.sendMessage(
                                onlinePlayer, "<red>Não foi possível resgatar sua recompensa diária.");
                    }
                }))
                .toCompletionStage();
    }
}
