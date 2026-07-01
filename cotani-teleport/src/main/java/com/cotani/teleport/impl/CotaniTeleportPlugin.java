package com.cotani.teleport.impl;

import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class CotaniTeleportPlugin extends JavaPlugin {
    private @Nullable TeleportModule module;

    @Override
    public void onEnable() {
        module = TeleportModule.create(this);
    }

    @Override
    public void onDisable() {
        if (module != null) {
            module.close();
        }
    }
}
