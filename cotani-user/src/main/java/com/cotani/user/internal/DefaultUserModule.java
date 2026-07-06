package com.cotani.user.internal;

import com.cotani.Cotani;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.user.api.UserModuleOptions;
import com.cotani.user.api.UserService;
import com.cotani.user.internal.cache.UserCache;
import com.cotani.user.internal.listener.UserListener;
import com.cotani.user.internal.mapper.UserMapper;
import com.cotani.user.internal.repository.CreateUsersTableMigration;
import com.cotani.user.internal.repository.StorageUserRepository;
import com.cotani.user.internal.service.InternalUserService;
import com.cotani.user.internal.service.SimpleUserService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

public final class DefaultUserModule implements com.cotani.user.api.UserModule {

    private final Cotani cotani;
    private final SimpleUserService userService;

    private DefaultUserModule(Cotani cotani, SimpleUserService userService) {
        this.cotani = cotani;
        this.userService = userService;
    }

    public static DefaultUserModule create(Plugin plugin, CotaniStorage storage, PaperTaskScheduler scheduler) {
        return create(plugin, storage, scheduler, UserModuleOptions.defaults());
    }

    public static List<Migration> migrations() {
        return List.of(new CreateUsersTableMigration());
    }

    private static void runAutoSave(SimpleUserService service, AtomicBoolean inProgress, Plugin plugin) {
        if (!inProgress.compareAndSet(false, true)) {
            return;
        }

        service.saveAll().whenComplete((_, throwable) -> {
            inProgress.set(false);
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to auto-save users", throwable);
            }
        });
    }

    public static DefaultUserModule create(
            Plugin plugin, CotaniStorage storage, PaperTaskScheduler scheduler, UserModuleOptions options) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(options, "options");

        var repository = new StorageUserRepository(storage, new UserMapper());
        var cache = new UserCache(repository);
        var service = new SimpleUserService(cache, repository);
        var listener = new UserListener(plugin, service, scheduler, options.loadFailureMessage());

        AtomicBoolean autoSaveInProgress = new AtomicBoolean(false);
        SchedulerTask autoSaveTask = options.autoSaveEnabled()
                ? scheduler.asyncTimer(
                        () -> runAutoSave(service, autoSaveInProgress, plugin),
                        options.autoSaveInterval(),
                        options.autoSaveInterval())
                : SchedulerTask.noop();

        Cotani cotani = Cotani.forPlugin(plugin)
                .with(() -> HandlerList.unregisterAll(listener))
                .with(autoSaveTask::cancel)
                .with(() -> saveAndClear(plugin, service))
                .build();

        try {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            return new DefaultUserModule(cotani, service);
        } catch (RuntimeException failure) {
            cotani.close();
            throw new IllegalStateException("Could not initialize user module", failure);
        }
    }

    private static void saveAndClear(Plugin plugin, SimpleUserService service) {
        CountDownLatch latch = new CountDownLatch(1);

        var _ = service.saveAll()
                .toCompletableFuture()
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((_, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to save users on shutdown", throwable);
                        latch.countDown();
                        return;
                    }
                    service.clearCache();
                    latch.countDown();
                });

        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out waiting for user shutdown save.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while waiting for user shutdown save.");
        }
    }

    @Override
    public UserService userService() {
        return userService;
    }

    public InternalUserService internalUserService() {
        return userService;
    }

    /**
     * Closes the module and propagates any shutdown failure as {@link com.cotani.CotaniCloseException}.
     *
     * <p>Errors from the final save-and-clear step are logged and swallowed; cache is only cleared when
     * the save succeeds.
     */
    @Override
    public void close() {
        cotani.close();
    }
}
