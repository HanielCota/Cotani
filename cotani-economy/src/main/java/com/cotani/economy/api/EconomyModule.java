package com.cotani.economy.api;

import com.cotani.Cotani;
import com.cotani.economy.EconomyService;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.user.api.UserService;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Public lifecycle handle for the economy module.
 */
public interface EconomyModule extends AutoCloseable {

    EconomyService economyService();

    @Override
    void close();

    final class Context {
        private final Plugin plugin;
        private final CotaniStorage storage;
        private final PaperTaskScheduler scheduler;
        private final UserService userService;
        private final @Nullable Cotani cotani;

        public Context(Plugin plugin, CotaniStorage storage, PaperTaskScheduler scheduler, UserService userService) {
            this(plugin, storage, scheduler, userService, null);
        }

        public Context(
                Plugin plugin,
                CotaniStorage storage,
                PaperTaskScheduler scheduler,
                UserService userService,
                @Nullable Cotani cotani) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.storage = Objects.requireNonNull(storage, "storage");
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            this.userService = Objects.requireNonNull(userService, "userService");
            this.cotani = cotani;
        }

        public Plugin plugin() {
            return plugin;
        }

        public CotaniStorage storage() {
            return storage;
        }

        public PaperTaskScheduler scheduler() {
            return scheduler;
        }

        public UserService userService() {
            return userService;
        }

        public @Nullable Cotani cotani() {
            return cotani;
        }
    }
}
