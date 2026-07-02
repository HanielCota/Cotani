package com.cotani.teleport.config;

import com.cotani.teleport.api.FeedbackSettings;
import com.cotani.teleport.api.PolicySettings;
import com.cotani.teleport.api.SafetySettings;
import com.cotani.teleport.api.TeleportOptions;
import java.time.Duration;

public final class TeleportOptionsFactory {

    private final TeleportOptions spawn;
    private final TeleportOptions admin;
    private final TeleportOptions silent;

    public TeleportOptionsFactory() {
        this(defaultSpawn(), defaultAdmin(), defaultSilent());
    }

    public TeleportOptionsFactory(TeleportOptions spawn, TeleportOptions admin, TeleportOptions silent) {
        this.spawn = spawn;
        this.admin = admin;
        this.silent = silent;
    }

    public TeleportOptions spawn() {
        return spawn;
    }

    public TeleportOptions admin() {
        return admin;
    }

    public TeleportOptions silent() {
        return silent;
    }

    private static TeleportOptions defaultSpawn() {
        return TeleportOptions.builder()
                .safety(SafetySettings.defaults())
                .policies(PolicySettings.builder()
                        .checkCombat(true)
                        .checkCooldown(true)
                        .checkRegion(true)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(true).build())
                .timeout(Duration.ofSeconds(10))
                .build();
    }

    private static TeleportOptions defaultAdmin() {
        return TeleportOptions.builder()
                .safety(SafetySettings.builder().safeLocation(false).build())
                .policies(PolicySettings.builder()
                        .checkCombat(false)
                        .checkCooldown(false)
                        .checkPermission(false)
                        .checkRegion(false)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(false).build())
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private static TeleportOptions defaultSilent() {
        return TeleportOptions.builder()
                .feedback(FeedbackSettings.builder()
                        .playEffects(false)
                        .sendMessages(false)
                        .build())
                .timeout(Duration.ofSeconds(10))
                .build();
    }
}
