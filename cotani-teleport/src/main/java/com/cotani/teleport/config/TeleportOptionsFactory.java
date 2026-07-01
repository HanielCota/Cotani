package com.cotani.teleport.config;

import com.cotani.teleport.api.FeedbackSettings;
import com.cotani.teleport.api.PolicySettings;
import com.cotani.teleport.api.SafetySettings;
import com.cotani.teleport.api.TeleportOptions;
import java.time.Duration;

public final class TeleportOptionsFactory {

    public TeleportOptions spawn() {
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

    public TeleportOptions admin() {
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

    public TeleportOptions silent() {
        return TeleportOptions.builder()
                .feedback(FeedbackSettings.builder()
                        .playEffects(false)
                        .sendMessages(false)
                        .build())
                .timeout(Duration.ofSeconds(10))
                .build();
    }
}
