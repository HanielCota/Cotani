package com.cotani.examples.showcase;

import com.cotani.economy.EconomyService;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.text.AudienceMessages;
import com.cotani.text.Placeholders;
import java.math.BigDecimal;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ShowcaseEconomyCommand implements CommandExecutor {
    private final EconomyService economyService;

    public ShowcaseEconomyCommand(EconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem executar este comando.");
            return true;
        }

        if (args.length == 0) {
            showBalance(player);
            return true;
        }

        if ("daily".equalsIgnoreCase(args[0])) {
            claimDailyReward(player);
            return true;
        }

        AudienceMessages.sendMessage(
                player, "<red>Uso correto: <yellow>/<label> [daily]</yellow>", Placeholders.unparsed("label", label));
        return true;
    }

    private void showBalance(Player player) {
        var playerId = player.getUniqueId();

        economyService.balance(playerId).whenComplete((balance, error) -> {
            if (error != null) {
                AudienceMessages.sendMessage(player, "<red>Erro ao consultar seu saldo. Tente novamente mais tarde.");
                return;
            }

            AudienceMessages.sendMessage(
                    player,
                    "<green>Seu saldo atual é: <gold><balance></gold> <yellow><symbol></yellow>",
                    Placeholders.unparsed("balance", balance.amount().toPlainString()),
                    Placeholders.unparsed("symbol", balance.currencyId().value()));
        });
    }

    private void claimDailyReward(Player player) {
        var playerId = player.getUniqueId();
        var rewardAmount = BigDecimal.valueOf(100);
        var operationId = EconomyOperationId.random();
        var reason = EconomyReason.system("daily_reward");

        economyService.deposit(playerId, rewardAmount, reason, operationId).whenComplete((tx, error) -> {
            if (error != null) {
                AudienceMessages.sendMessage(player, "<red>Não foi possível resgatar sua recompensa diária.");
                return;
            }

            AudienceMessages.sendMessage(
                    player,
                    "<green>Você resgatou com sucesso <gold><amount></gold> moedas!",
                    Placeholders.unparsed("amount", tx.amount().toPlainString()));
        });
    }
}
