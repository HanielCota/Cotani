<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-job

</div>

Jobs persistentes e assíncronos para Paper e Folia, construídos sobre `cotani-task` e seu
`PersistentTaskStore`.

```java
var jobs = CotaniJobs.create(scheduler, new FilePersistentTaskStore(plugin.getDataFolder().toPath().resolve("jobs")));
jobs.registerHandler("cleanup", context -> cleanupService.cleanupAsync());

jobs.scheduleAsync(JobRequest.recurring(
                "cleanup",
                new byte[0],
                Duration.ofMinutes(1),
                Duration.ofHours(1)))
        .thenAccept(handle -> plugin.getLogger().info("Job scheduled: " + handle.id()));

// Execute during startup, after all handlers have been registered.
jobs.recoverPendingAsync();
```

## Garantias

- handlers são nomeados e registrados por injeção, sem `Player`, `World` ou objetos Bukkit no núcleo;
- o registro é salvo antes do dispatch e permanece disponível durante shutdown para recuperação;
- retries são limitados por tentativa, usam backoff exponencial, mantêm o mesmo `JobId` lógico e o mesmo `JobExecutionId`;
- cada ocorrência recorrente recebe um novo `JobExecutionId`, permitindo idempotência e auditoria por execução;
- jobs recorrentes são reprogramados somente após sucesso do handler;
- cancelamento explícito remove o registro persistente através de `cancelAsync`; durante um handler em execução, a remoção
  aguarda a conclusão real da etapa assíncrona;
- falhas podem ser observadas por `JobFailureListener` sem alterar a semântica de execução;
- `handlerTimeout` funciona como watchdog; ele não inicia retry enquanto a etapa original ainda estiver pendente,
  evitando execuções sobrepostas. Handlers que produzem efeitos externos devem continuar sendo idempotentes;
- a recuperação é limitada por `JobServiceOptions.maxRecoveryBatch()` e chamadas concorrentes de recuperação são
  coalescidas;
- todas as operações públicas de persistência e execução são compostas com `CompletionStage`.

O store deve ter um único proprietário ativo por conjunto de jobs. Para execução distribuída entre vários servidores,
adicione um adapter de claim/lease ou um lock distribuído antes de compartilhar o mesmo `PersistentTaskStore`.

O serviço não executa automaticamente a recuperação: chame `recoverPendingAsync()` depois de registrar os handlers.
Isso evita perder jobs apenas porque o plugin ainda não terminou seu bootstrap.

Para persistência em arquivo use `FilePersistentTaskStore`. Para testes ou tarefas efêmeras, use
`CotaniJobs.inMemory(scheduler)`.
