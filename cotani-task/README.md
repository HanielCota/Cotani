# cotani-task

Scheduler and async execution framework for Paper and Folia. Manages global, region, entity, and async tasks safely, and provides `TaskChain` for chaining execution between threads.

## Usage

```java
PaperTaskScheduler scheduler = SchedulerFactory.create(plugin);

// Run asynchronously and return to the main thread
scheduler.supplyAsync(() -> fetchUserData(uuid))
    .thenGlobal(data -> applyToPlayer(data))
    .toCompletionStage();
```
