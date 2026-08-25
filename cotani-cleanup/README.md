# cotani-cleanup

Limpeza segura e assíncrona de entidades carregadas em mundos Paper/Folia.

O módulo separa a política de limpeza do acesso ao mundo. `CleanupPolicy` é uma allow-list explícita: por padrão,
apenas itens dropados e orbes de XP com pelo menos cinco minutos podem ser selecionados. Entidades nomeadas,
persistentes e domesticadas são protegidas por padrão.

## Uso

```java
var cleanup = CotaniCleanups.paper(scheduler, eventBus);

var policy = CleanupPolicy.builder()
        .targets(List.of(CleanupTarget.ARROW))
        .minimumAge(Duration.ofMinutes(2))
        .protectedTags(List.of("myplugin:protected"))
        .maxEntities(5_000)
        .build();

cleanup.previewAsync(cleanup.newRequest(policy, "admin-preview"))
        .thenAccept(report -> logger.info("Candidates: " + report.matchedEntities()));

cleanup.executeAsync(cleanup.newRequest(policy, "manual-cleanup"))
        .thenAccept(report -> logger.info("Removed: " + report.removedEntities()));

var schedule = CotaniCleanups.scheduler(scheduler, cleanup)
        .schedule(policy, Duration.ofMinutes(5), Duration.ofMinutes(5), "scheduled-cleanup");
```

## Garantias

- `previewAsync` nunca remove entidades.
- O executor Paper captura apenas valores imutáveis durante o scan.
- O scan captura apenas coordenadas imutáveis de chunks e inspeciona cada chunk na thread da região.
- A remoção usa o scheduler da própria entidade, permanecendo segura mesmo se ela se mover.
- Cada operação é serializada e possui limite de fila para evitar scans sobrepostos e pressão ilimitada.
- O executor revalida idade, alvo e proteções antes de remover uma entidade que pode ter mudado desde o scan.
- Os eventos são best effort e não fazem uma limpeza falhar.
- O relatório diferencia entidades elegíveis de entidades selecionadas pelo limite e inclui falhas detalhadas.
- O núcleo não depende de banco de dados; relatórios podem ser enviados ao módulo de auditoria pelo host.

O host deve registrar o serviço no lifecycle do plugin e fechá-lo com `closeAsync()` no shutdown.
