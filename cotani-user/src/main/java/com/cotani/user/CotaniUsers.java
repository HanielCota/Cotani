package com.cotani.user;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.user.api.UserModule;
import com.cotani.user.api.UserModuleOptions;
import com.cotani.user.internal.DefaultUserModule;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class CotaniUsers {

    private CotaniUsers() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static UserModule create(Plugin plugin, CotaniStorage storage, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(scheduler, "scheduler");
        return DefaultUserModule.create(plugin, storage, scheduler);
    }

    public static UserModule create(
            Plugin plugin, CotaniStorage storage, PaperTaskScheduler scheduler, UserModuleOptions options) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(options, "options");
        return DefaultUserModule.create(plugin, storage, scheduler, options);
    }

    public static List<Migration> migrations() {
        return DefaultUserModule.migrations();
    }
}
