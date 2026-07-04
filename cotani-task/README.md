# cotani-task

Módulo de scheduling e execução assíncrona para Paper/Folia. Abstrai tarefas globais, region, entity, async, debounce, retries, timeouts, persistent tasks e cadeias de tarefas (`TaskChain`).

## Responsabilidade

- Executar código na thread correta (main/global/region/entity/async).
- Evitar `Thread.sleep`, `join()` e `get()` no código de aplicação.
- Fornecer `TaskChain` para compor fluxos async que retornam à main thread quando necessário.
- Suportar tarefas persistentes entre reinicializações.
- Oferecer retries com backoff, jitter e limite de tentativas.
- Expor métricas básicas de execução.

## Stack

- Java 21+
- Paper API
- JSpecify
- Virtual threads (opcional)

## Uso básico

```java
PaperTaskScheduler scheduler = SchedulerFactory.create(this);

scheduler.asyncLater(() -> getLogger().info("Executado async"), Duration.ofSeconds(1));

scheduler.global(() -> Bukkit.broadcast(Component.text("Na main thread")));

scheduler.entity(player, () -> player.sendMessage("Sincronizado com a entidade"));
```

## TaskChain

```java
scheduler.supplyAsync(() -> UUID.randomUUID())
    .thenGlobal(uuid -> Bukkit.getPlayer(uuid)) // volta para main thread
    .thenAsync(player -> carregarDados(player.getUniqueId()))
    .toCompletionStage()
    .whenComplete((result, error) -> { /* ... */ });
```

## Retry e timeout

```java
scheduler.supplyAsync("load", () -> fetchRemoteData())
    .timeout(Duration.ofSeconds(5))
    .retry(RetryPolicy.exponentialWithJitter(3, Duration.ofMillis(200)))
    .toCompletionStage();
```

## Tarefas persistentes

```java
scheduler.persistAndRun(
    "daily-reward",
    Duration.ofHours(24),
    payload,
    recoveredPayload -> process(recoveredPayload)
);
```

## API pública

| Classe/Interface | Descrição |
|------------------|-----------|
| `PaperTaskScheduler` | Scheduler principal: async, global, region, entity, timers, debounce, métricas. |
| `SchedulerFactory` | Fábrica para criar schedulers com opções e handler de exceções customizados. |
| `SchedulerOptions` | Configurações: virtual threads, cancelamento de tasks no close, timeout de shutdown. |
| `TaskChain<T>` | Cadeia fluente para compor operações async/global/region/entity. |
| `RetryPolicy` | Política de retry com backoff fixo, exponencial e jitter. |
| `TaskContext` | Contexto de execução com metadados e tempo decorrido. |
| `TaskMetadata` | Metadados nomeados de uma tarefa. |
| `SchedulerTask` | Handle de uma tarefa agendada. |
| `PersistentTask` | Representação de uma tarefa persistente. |
| `PersistentTaskStore` | Abstração de store para tarefas persistentes. |
| `TaskMetrics` / `TaskMetricSnapshot` | Métricas de execução. |

## Estrutura de pacotes

```text
com.cotani.task
├── api
│   ├── PaperTaskScheduler.java
│   ├── TaskChain.java
│   ├── RetryPolicy.java
│   ├── SchedulerOptions.java
│   ├── SchedulerTask.java
│   ├── TaskContext.java
│   ├── TaskMetadata.java
│   └── ...
├── scheduler
│   └── SchedulerFactory.java
├── persistence
│   ├── PersistentTask.java
│   ├── PersistentTaskStore.java
│   └── FilePersistentTaskStore.java
├── metrics
│   ├── TaskMetrics.java
│   └── TaskMetricSnapshot.java
├── throttle
│   ├── RateLimiter.java
│   ├── TokenBucketRateLimiter.java
│   └── TaskThrottler.java
├── bucket
│   ├── TaskBucket.java
│   └── TaskBucketFactory.java
├── impl
│   ├── scheduler
│   ├── dispatch
│   ├── chain
│   ├── task
│   ├── executor
│   └── exception
└── util
    ├── CompletionStages.java
    ├── TaskChains.java
    └── VoidResult.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":task"))
}
```

## Integração

- `cotani-cache` e `cotani-storage` usam `PaperTaskScheduler` para executar I/O fora da main thread.
- `cotani-config` usa `TaskChain` para reload assíncrono.
