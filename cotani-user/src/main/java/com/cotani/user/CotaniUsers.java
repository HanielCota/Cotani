package com.cotani.user;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.user.api.UserModule;
import com.cotani.user.api.UserModuleOptions;
import com.cotani.user.internal.DefaultUserModule;
import java.util.List;
import org.bukkit.plugin.Plugin;

public final class CotaniUsers {

    private CotaniUsers() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static UserModule create(Plugin plugin, CotaniStorage storage, PaperTaskScheduler scheduler) {
        return DefaultUserModule.create(plugin, storage, scheduler);
    }

    public static UserModule create(
            Plugin plugin, CotaniStorage storage, PaperTaskScheduler scheduler, UserModuleOptions options) {
        return DefaultUserModule.create(plugin, storage, scheduler, options);
    }

    public static List<Migration> migrations() {
        return DefaultUserModule.migrations();
    }
}
