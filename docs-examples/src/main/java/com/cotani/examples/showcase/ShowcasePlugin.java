package com.cotani.examples.showcase;

import com.cotani.Cotani;
import com.cotani.economy.EconomyBootstrap;
import com.cotani.gui.CotaniGuiModule;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.scheduler.SchedulerFactory;
import com.cotani.teleport.CotaniTeleports;
import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.adapter.RegionProtectionAdapter;
import com.cotani.teleport.api.PendingTeleportService;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ShowcasePlugin extends JavaPlugin {
    private @Nullable Cotani lifecycle;
    private @Nullable EconomyBootstrap economyBootstrap;

    @Override
    public void onEnable() {
        PaperTaskScheduler scheduler = SchedulerFactory.create(this);
        this.economyBootstrap = EconomyBootstrap.createDefault();
        PendingTeleportService pendingTeleportService = CotaniTeleports.create(
                        this, CombatAdapter.noop(), RegionProtectionAdapter.noop(), scheduler)
                .pendingTeleportService();
        CotaniGuiModule guiModule = CotaniGuiModule.create(this);

        this.lifecycle = Cotani.forPlugin(this)
                .with(guiModule)
                .withAsync(scheduler::closeAsync)
                .build();

        registerCommand("cotanieco", new ShowcaseEconomyCommand(economyBootstrap.service()));
        registerCommand("cotanispawn", new ShowcaseTeleportCommand(pendingTeleportService));
        registerCommand("cotanirecycler", new ShowcaseRecyclerCommand());
    }

    @Override
    public void onDisable() {
        if (economyBootstrap != null) {
            economyBootstrap.close();
        }

        var currentLifecycle = lifecycle;
        if (currentLifecycle == null) {
            return;
        }

        var _ = currentLifecycle.closeAsync().whenComplete((_, error) -> {
            if (error != null) {
                getLogger().log(Level.SEVERE, "Falha ao desativar recursos do Cotani", error);
            }
        });
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            return;
        }

        command.setExecutor(Objects.requireNonNull(executor, "executor"));
    }
}
