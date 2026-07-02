package com.cotani.teleport.config;

import com.cotani.config.CotaniConfig;
import com.cotani.config.CotaniConfigs;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.ExecutionSettings;
import com.cotani.teleport.api.FeedbackSettings;
import com.cotani.teleport.api.PlayerSettings;
import com.cotani.teleport.api.PolicySettings;
import com.cotani.teleport.api.SafeLocationOptions;
import com.cotani.teleport.api.SafetySettings;
import com.cotani.teleport.api.TeleportMessages;
import com.cotani.teleport.api.TeleportOptions;
import org.bukkit.plugin.Plugin;

public final class TeleportConfiguration implements AutoCloseable {

    private final CotaniConfigs configs;
    private final TeleportMessages messages;
    private final TeleportOptionsFactory options;

    private TeleportConfiguration(CotaniConfigs configs, TeleportMessages messages, TeleportOptionsFactory options) {
        this.configs = configs;
        this.messages = messages;
        this.options = options;
    }

    public static TeleportConfiguration load(Plugin plugin, PaperTaskScheduler scheduler) {
        CotaniConfigs configs = CotaniConfigs.create(plugin)
                .scheduler(scheduler)
                .file("config.yml")
                .file("messages.yml")
                .load();
        return new TeleportConfiguration(
                configs, loadMessages(configs.file("messages.yml")), loadOptions(configs.file("config.yml")));
    }

    public TeleportMessages messages() {
        return messages;
    }

    public TeleportOptionsFactory options() {
        return options;
    }

    @Override
    public void close() {
        configs.close();
    }

    private static TeleportMessages loadMessages(CotaniConfig config) {
        TeleportMessages defaults = TeleportMessages.defaults();
        return TeleportMessages.builder()
                .blockedByCombat(config.getComponent("blocked-by-combat", defaults.blockedByCombat()))
                .blockedByCooldown(config.getComponent("blocked-by-cooldown", defaults.blockedByCooldown()))
                .blockedByPermission(config.getComponent("blocked-by-permission", defaults.blockedByPermission()))
                .blockedByRegion(config.getComponent("blocked-by-region", defaults.blockedByRegion()))
                .build();
    }

    private static TeleportOptionsFactory loadOptions(CotaniConfig config) {
        return new TeleportOptionsFactory(
                loadPreset(config, "presets.spawn", new TeleportOptionsFactory().spawn()),
                loadPreset(config, "presets.admin", new TeleportOptionsFactory().admin()),
                loadPreset(config, "presets.silent", new TeleportOptionsFactory().silent()));
    }

    private static TeleportOptions loadPreset(CotaniConfig config, String path, TeleportOptions fallback) {
        SafeLocationOptions safeLocation = new SafeLocationOptions(
                config.getInt(
                        path + ".safety.horizontal-radius",
                        fallback.safeLocationOptions().horizontalRadius()),
                config.getInt(
                        path + ".safety.vertical-radius",
                        fallback.safeLocationOptions().verticalRadius()),
                config.getBoolean(
                        path + ".safety.avoid-liquids",
                        fallback.safeLocationOptions().avoidLiquids()),
                config.getBoolean(
                        path + ".safety.avoid-hazards",
                        fallback.safeLocationOptions().avoidHazards()),
                config.getBoolean(
                        path + ".safety.respect-world-border",
                        fallback.safeLocationOptions().respectWorldBorder()));

        return TeleportOptions.builder()
                .execution(new ExecutionSettings(config.getBoolean(path + ".execution.async", fallback.async())))
                .safety(new SafetySettings(
                        config.getBoolean(path + ".safety.safe-location", fallback.safeLocation()), safeLocation))
                .policies(new PolicySettings(
                        config.getBoolean(path + ".policies.check-combat", fallback.checkCombat()),
                        config.getBoolean(path + ".policies.check-cooldown", fallback.checkCooldown()),
                        config.getDuration(path + ".policies.cooldown-duration", fallback.cooldownDuration()),
                        config.getBoolean(path + ".policies.check-permission", fallback.checkPermission()),
                        config.getBoolean(path + ".policies.check-region", fallback.checkRegion())))
                .player(new PlayerSettings(
                        config.getBoolean(path + ".player.preserve-velocity", fallback.preserveVelocity()),
                        config.getBoolean(path + ".player.dismount", fallback.dismount()),
                        config.getBoolean(path + ".player.close-inventory", fallback.closeInventory())))
                .feedback(new FeedbackSettings(
                        config.getBoolean(path + ".feedback.play-effects", fallback.playEffects()),
                        config.getBoolean(path + ".feedback.send-messages", fallback.sendMessages())))
                .timeout(config.getDuration(path + ".timeout", fallback.timeout()))
                .build();
    }
}
