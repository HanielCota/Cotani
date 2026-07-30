# cotani-core

Core bootstrapping, lifecycle management, and shared exceptions for the Cotani framework.

## Overview

`cotani-core` provides the infrastructure for starting and stopping Cotani modules in a Minecraft server environment. It ensures that resources, schedulers, caches, and storage engines are booted in a predictable order and closed cleanly during server shutdown to prevent memory leaks and data corruption.

## Features

- **Centralized Lifecycle Control**: Easy registration and ordered shutdown of `AutoCloseable` services.
- **Async Lifecycle Contract**: `AsyncCloseable` lets modules expose coalesced, non-blocking shutdown without requiring their full API.
- **Reverse-Order Shutdown**: Automatically closes registered resources in the reverse order they were registered.
- **Framework Exceptions**: Shared base exception types (such as `CotaniCloseException`) for robust error handling.

## Usage

Create the `Cotani` instance during plugin startup (`onEnable`) and register all services. Ensure it is closed during shutdown (`onDisable`).

```java
import com.cotani.Cotani;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.scheduler.SchedulerFactory;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    private Cotani cotani;

    @Override
    public void onEnable() {
        PaperTaskScheduler scheduler = SchedulerFactory.create(this);
        cotani = Cotani.forPlugin(this)
            .withAsync(scheduler::closeAsync)
            .build();
    }

    @Override
    public void onDisable() {
        if (cotani != null) {
            cotani.closeAsync().exceptionally(error -> {
                getLogger().log(java.util.logging.Level.SEVERE, "Could not close Cotani", error);
                return null;
            });
        }
    }
}
```

## Hard Rules & Best Practices

1. **Mandatory Lifecycle Mapping**: Create `Cotani` in `onEnable` and start `closeAsync()` in `onDisable`.
2. **Register All Closeables**: Every `AutoCloseable` resource created at plugin startup (like schedulers, database drivers, caches) must be registered with `Cotani.forPlugin(plugin).with(resource).build()` to guarantee safe disposal.
3. **No Service Locator Abuse**: Do not use `Cotani` as a global service locator or singleton. Its sole responsibility is closeable lifecycle management. Use constructor injection instead.
4. **No Main-Thread Blocking**: `Cotani.close()` is only for non-server threads. On Paper's primary thread, use `closeAsync()` and observe its failed stage.

## Anti-Patterns

- Holding resources in plugin fields without registering them in the `Cotani` builder (leads to leaks on reload).
- Manually invoking `.close()` on individual resources when they are already registered in `Cotani`.
- Calling `cotani.close()` multiple times.
