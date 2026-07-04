package com.cotani.economy.api;

import com.cotani.Cotani;
import com.cotani.economy.EconomyService;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Public lifecycle handle for the economy module.
 */
@NullMarked
public interface EconomyModule extends AutoCloseable {

    EconomyService economyService();

    @Override
    void close();

    record Context(
            Plugin plugin,
            CotaniStorage storage,
            PaperTaskScheduler scheduler,
            @Nullable Cotani cotani) {
        public Context(Plugin plugin, CotaniStorage storage, PaperTaskScheduler scheduler) {
            this(plugin, storage, scheduler, null);
        }

        public Context {
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(scheduler, "scheduler");
        }
    }
}
