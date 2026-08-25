# Auditoria completa de arquitetura do Cotani

> Relatório histórico: os números de versão e o baseline de dependências abaixo descrevem o ambiente no momento da
> auditoria. Para o estado atual do projeto, consulte o [README](https://github.com/HanielCota/Cotani/blob/master/README.md), o catálogo Gradle e a
> [documentação de arquitetura](../architecture.md).

Data: 28 de julho de 2026  
Escopo: todos os 14 módulos, build, workflows, documentação, APIs públicas, testes e lifecycle.  
Método: leitura estática, execução de baseline, testes determinísticos com futures/latches, integração SQLite, correções pequenas e nova validação global.

## Resumo executivo

| Severidade | Encontrados | Corrigidos | Restantes |
|---|---:|---:|---:|
| P0 | 1 | 1 | 0 |
| P1 | 17 | 17 | 0 |
| P2 | 12 | 12 | 0 |
| P3 | 3 | 3 | 0 |
| **Total** | **33** | **33** | **0** |

Os defeitos de maior impacto estavam concentrados na identidade de operações econômicas, semântica de timeout/retry, transações, lifecycle de storage, persistência fora de ordem do cache, sessões de usuário e teleportes concorrentes. Todos os 33 achados identificados foram corrigidos e possuem proteção automatizada. Não há achado funcional conhecido em aberto ao final; as limitações externas de infraestrutura e segurança operacional estão explicitadas nas notas finais.

## Baseline e build

### Estado inicial

- Gradle wrapper: 9.6.1.
- Launcher/daemon local: Azul Zulu 25.0.3 LTS, Windows 11 amd64.
- Toolchain solicitada: Java 25.
- Documentação/CI antes da revisão: Java 21, enquanto a toolchain já exigia Java 25.
- Paper antes da revisão: `26.2-rc-2.build.9-alpha`.
- `gradlew clean check`: sucesso em 1 s; 97 tarefas acionáveis, 27 executadas, 56 do cache e 14 atualizadas.
- `gradlew build`: sucesso em 11 s; 130 tarefas acionáveis, 49 executadas e 81 atualizadas.
- Warnings: CDS/boot classpath do Mockito e acesso nativo do driver SQLite.
- Tarefas `processResources`/`processTestResources` sem fontes apareceram como `NO-SOURCE`; nenhum teste foi ignorado.

### Baseline escolhido

Java 25, Paper API `26.2.build.85-stable`, Gradle 9.6.1 e Palantir Java Format 2.96.0. README, CONTRIBUTING, CI, release, Sonar e catálogo de versões agora concordam. Paper 26.2 requer Java 25 na linha atual; usar RC/alpha na linha principal expunha consumidores a mudanças pré-release sem necessidade.

### Estado final

- `gradlew spotlessApply`: sucesso.
- `gradlew spotlessCheck validateModuleArchitecture compileJava compileTestJava --no-build-cache`: sucesso.
- `gradlew clean check --no-build-cache --no-configuration-cache`, em daemon novo: sucesso em 50 s; 97 tarefas acionáveis, todas executadas.
- `gradlew build --no-build-cache`: sucesso em 14 s; 130 tarefas acionáveis, 49 executadas e 81 atualizadas.
- Testes: 610 em 94 suites; 0 falhas e 0 erros. Quatro casos Testcontainers foram ignorados localmente porque Docker não está instalado; eles permanecem habilitados automaticamente em ambiente com Docker.
- Uma execução no daemon que ainda carregava o formatador anterior falhou antes dos testes por `NoClassDefFoundError` em classe do Guava. A reprodução isolada, a reinicialização do daemon e o build limpo confirmaram estado obsoleto do classloader, não dependência ausente; Palantir Java Format 2.96.0 e o paralelismo foram preservados.
- Warnings restantes: acesso nativo do `sqlite-jdbc` e aviso CDS do Byte Buddy/Mockito; nenhum representa falha dos testes.

## Inventário arquitetural

Não há ciclo no grafo Gradle. A tarefa `validateModuleArchitecture` também rejeita import de `impl`/`internal` entre módulos.

```text
core
  -> dependências: nenhuma
  -> execução: thread chamadora; closeAsync compõe os fechamentos registrados
  -> estado: módulos/closeables e future de fechamento
  -> fecha: módulos registrados
  -> Bukkit: detecção da thread primária no adaptador close()

task
  -> dependências: core
  -> execução: async limitado, timers, global, região e entidade Paper/Folia
  -> estado: debounce, retries, métricas, tarefas persistentes
  -> fecha: executor, timers e tarefas pertencentes ao plugin
  -> Bukkit: schedulers async/global/region/entity e resolução por UUID

text -> core; thread chamadora; LRU sincronizado de 512 templates; nenhum recurso; Adventure/MiniMessage
item -> core,text; thread válida do servidor; cache Caffeine de profiles; resolver fechável; ItemStack/Profile Bukkit
config -> core,task,text; I/O no executor async; snapshots YAML/serializers; arquivos; registries Bukkit
storage -> task,text; executor SQLite único ou SQL configurado; repositories/provider; Hikari/SQLite/executor; JDBC e config Bukkit
cache -> core,task,storage,config; executor task+Caffeine; entries/dirty/save lanes; autosave/removal callbacks; listener Player
cooldown -> core,task,cache,storage,config; thread chamadora/storage; mapas por chave/jogador; nenhum recurso próprio; UUID apenas
user -> storage,task,core,text; executor do repository; cache/load/write lanes; listener fechável; Player apenas no listener
economy -> core,task,storage,config,text; executor injetado/storage; contas/operações/cache; storage/config/formatter; eventos Bukkit na thread correta
teleport -> core,task,cooldown,text,config; entidade/região/Paper async; pipeline por UUID; pending/config/cooldown; Player/World/Location
event -> nenhuma; chamadora ou executor explícito; subscriptions/cache/lock; subscriptions fecháveis; nenhuma Bukkit
metrics -> config,cache,storage,task; HTTP virtual threads; registry/server; fecha servidor e registry; nenhuma Bukkit
gui -> text,item; thread proprietária do viewer; panels/state/subscriptions; fecha listeners/panels; Inventory/Player/Paper
```

Fluxo Paper/Folia verificado:

```text
evento/comando no contexto do jogador
  -> captura UUID e valores imutáveis
  -> I/O/processamento no executor explícito
  -> TaskChain global/região/entidade
  -> acesso Bukkit/Paper
  -> persistência/auditoria assíncrona
```

O grafo real corresponde ao README depois da inclusão explícita de `metrics` e `gui`. As APIs são interfaces/records nos pacotes de domínio; declarações públicas necessárias em `impl`/`internal` são marcadas com `@InternalApi`, e a validação arquitetural impede novas exposições não marcadas.

## Mapa de lifecycle e propriedade

| Recurso | Proprietário | Início | Fechamento e invariante |
|---|---|---|---|
| PaperTaskScheduler | plugin/Cotani | `SchedulerFactory.create` | `closeAsync` canônico; cancela tarefas/debounce e coalesce chamadas |
| VirtualThreadExecutor | task | bootstrap do scheduler | fila 4096, concorrência limitada, rejeição; shutdown em thread dedicada no async close |
| CotaniStorage | módulo storage | `startAsync` | `NEW -> STARTING -> RUNNING -> CLOSING -> CLOSED`, ou `FAILED`; provider/executor fechados uma vez |
| Hikari/SQLite | CotaniStorage | startup | fechados em sucesso parcial, falha e close |
| DataCache | módulo cache | builder | `closeAsync` aguarda callbacks, lanes e dirty saves; rejeita mutações depois do close |
| CotaniConfigs | módulo config | `loadAsync` | I/O apenas fora da thread primária; `saveAsync`/`reloadAsync`; limpa registry no close |
| User/Economy/Teleport | seus módulos | bootstrap | listeners, cache, config, pending e serviços fechados pelo módulo/Cotani |
| PrometheusServer | metrics | `create` quando enabled | loopback por padrão; `stop(0)` e `shutdownNow`, sem espera na thread chamadora |
| GuiPanel/Property.Subscription | gui/panel | abertura/observe | panel fecha subscriptions e inventário; módulo fecha listener |

## Achados corrigidos

## [P0] OperationId econômico não identificava a requisição original

### Local
- módulo: economy
- arquivo: `EconomyOperationFingerprint.java`, `InMemoryEconomyStore.java`, `SqlEconomyStore.java`
- linhas: 15-74; 217-263; 203-223
- método: `executeIdempotent`/`requireMatch`

### Problema
Uma chave idempotente já gravada podia ser reutilizada com outro tipo, conta, moeda, valor ou motivo e retornar a transação antiga como se a nova operação tivesse ocorrido.

### Cenário de falha
Um depósito com ID X é confirmado; o cliente reutiliza X para um saque. A resposta antiga é devolvida e o chamador pode confirmar uma alteração financeira que nunca foi aplicada.

### Impacto
Integridade financeira, reconciliação incorreta e possível perda/duplicação lógica de dinheiro.

### Causa raiz
Idempotência era comparada somente por `EconomyOperationId`, sem fingerprint persistente da intenção.

### Correção proposta
Vincular ID ao fingerprint completo, preservar retry idêntico e rejeitar conflito com `DuplicateEconomyOperationException`; manter constraint única e transação no SQL.

### Teste de regressão
`operationIdIsPersistentAndBoundToTheOriginalRequest` e testes equivalentes do store em memória; `concurrentWithdrawalsCannotSpendTheSameBalanceTwice` valida atomicidade SQLite.

### Compatibilidade
Retry idêntico não muda. Reuso conflitante, antes ambíguo, agora falha explicitamente.

## [P1] Timeout alterava o future compartilhado e mascarava falhas

### Local
- módulo: task
- arquivo: `DefaultTaskChain.java`
- linhas: 164-171, 344-368
- método: `timeout`/`withTimeout`

### Problema
Aplicar timeout diretamente ao future-fonte contaminava chains irmãs e a tradução ampla de erro podia esconder a exceção original.

### Cenário de falha
Duas chains derivam do mesmo future; uma usa timeout curto. O timeout completa a fonte excepcionalmente e a outra falha sem ter solicitado deadline.

### Impacto
Falha incompatível com o contrato e efeitos finais duplicados ou omitidos.

### Causa raiz
Uso mutável de `orTimeout` na instância compartilhada e classificação insuficiente da causa.

### Correção proposta
Aplicar timeout em `copy()`, converter somente `TimeoutException`, validar duração e deixar uma única conclusão lógica.

### Teste de regressão
`timeoutPreservesOriginalException`, `timeoutDoesNotCompleteSharedSource`, `timeoutFailsWhenFutureDoesNotComplete`, `timeoutDoesNotAffectFastChain` e `timeoutRejectsNonPositiveAndExcessiveDurations`.

### Compatibilidade
Sem mudança de assinatura; chains irmãs deixam de sofrer efeito colateral.

## [P1] Retry reutilizava operação não repetível

### Local
- módulo: task
- arquivo: `DefaultTaskChain.java`, `TaskChain.java`
- linhas: 173-188, 370-430
- método: `retry`/`RetryController`

### Problema
Um `CompletionStage` já iniciado podia ser tratado como nova tentativa, sem reexecutar a operação; cancelamento e delay inválido não tinham semântica segura.

### Cenário de falha
O primeiro acesso ao banco falha; três retries observam o mesmo future concluído em vez de executar três tentativas.

### Impacto
Resiliência falsa, contagem incorreta e trabalho agendado após cancelamento.

### Causa raiz
Ausência de distinção entre fábrica repetível e stage externo.

### Correção proposta
Propagar fábrica somente nas chains criadas por supplier/scheduler, rejeitar retry de stage externo, reagendar a fábrica e cancelar o delay pendente.

### Teste de regressão
`retryReexecutesRepeatableSupplierExactNumberOfTimes`, `retryRejectsExternalNonRepeatableStage` e `cancellingRetryCancelsScheduledAttempt`.

### Compatibilidade
Retry sobre stage externo agora lança `IllegalStateException`; migração: criar a chain por `supplyAsync(Supplier)`.

## [P1] Debounce permitia execução antiga e remoção da tarefa nova

### Local
- módulo: task
- arquivo: `ModernPaperTaskScheduler.java`
- linhas: 253-277, 472-520
- método: `debounce`/`DebounceTask`

### Problema
Delay zero podia executar antes da referência ser publicada; uma tarefa antiga também podia remover a entrada da geração nova.

### Cenário de falha
Tarefa A é substituída por B enquanto A dispara; A executa ou remove B de `pendingDebounces`.

### Impacto
Execução duplicada, NPE e perda do debounce mais recente.

### Causa raiz
Publicação não atômica da referência e remoção apenas por chave.

### Correção proposta
Token por geração, attach tolerante a execução antecipada e `remove(name, this)` condicional.

### Teste de regressão
`supersededDebounceCannotExecuteAfterReplacement` e `zeroDelayDebounceCanRunBeforeSchedulerReturns`.

### Compatibilidade
Sem mudança pública.

## [P1] Cancelamento persistente deixava tarefa recuperável

### Local
- módulo: task
- arquivo: `ModernPaperTaskScheduler.java`
- linhas: 280-331, 526-584
- método: `persistAndRun`/`PersistentSchedulerTask.cancel`

### Problema
Cancelar o handle podia cancelar apenas o agendamento Paper, mantendo o registro que seria recuperado no reinício.

### Cenário de falha
Backup persistente é cancelado; o servidor reinicia; o registro ainda pendente executa apesar do cancelamento explícito.

### Impacto
Efeito persistente inesperado e violação de cancelamento.

### Causa raiz
Não havia coordenação entre persistência assíncrona, cancelamento e remoção do store.

### Correção proposta
Cancelamento explícito marca o registro concluído antes/depois do save; shutdown conserva o registro para semântica at-least-once.

### Teste de regressão
`cancellingPersistedTaskRemovesItsRecoveryRecord` e `cancellationBeforePersistenceIsRemovedAfterSave`.

### Compatibilidade
Semântica documentada em `PaperTaskScheduler`: cancelamento remove; close permite recuperação.

## [P1] Saturação e shutdown do scheduler bloqueavam o chamador

### Local
- módulo: task
- arquivo: `VirtualThreadExecutor.java`, `PaperTaskScheduler.java`, `ModernPaperTaskScheduler.java`
- linhas: 57-72, 109-151; 126-134; 406-468
- método: `createTaskExecutor`/`closeAsync`

### Problema
`Semaphore.acquireUninterruptibly` e `CallerRunsPolicy` deslocavam bloqueio/trabalho para o chamador; `close()` aguardava até cinco segundos sem alternativa pública assíncrona.

### Cenário de falha
256 tarefas estão presas em I/O e um evento Paper envia a próxima tarefa: o tick fica bloqueado esperando vaga ou executa o trabalho no game loop.

### Impacto
Travamento relevante da thread principal e shutdown lento.

### Causa raiz
Backpressure implementado no produtor e contrato apenas `AutoCloseable`.

### Correção proposta
Pool com concorrência e fila de 4096 limitadas, `AbortPolicy`, `closeAsync` coalescido em thread dedicada, rejeição após close e guarda no adaptador síncrono.

### Teste de regressão
`saturationRejectsWithoutBlockingOrRunningOnCaller` e `closeAsyncCoalescesAndRejectsNewTasks`.

### Compatibilidade
Sob saturação extrema agora há `RejectedExecutionException`, não bloqueio implícito. Consumidores devem aplicar política explícita de admissão/retry.

## [P1] Transações não cobriam falha síncrona, null e espera externa

### Local
- módulo: storage
- arquivo: `TransactionManager.java`, `QueryExecutor.java`
- linhas: 39-133; 96-122, 279-343
- método: `transaction`/`finishTransaction`

### Problema
Exceção antes do retorno do stage, stage `null` e falhas de commit/rollback podiam pular restauração/close; um stage externo incompleto mantinha conexão e transação abertas.

### Cenário de falha
`operation.apply` lança; a conexão fica com `autoCommit=false` ou aberta. Em outro caso, a operação aguarda HTTP mantendo uma conexão do pool.

### Impacto
Vazamento contínuo de conexão, pool exaurido e transação pendente.

### Causa raiz
Cleanup estava ligado apenas à conclusão assíncrona normal.

### Correção proposta
Estado transacional explícito, rollback/restore/close em todos os caminhos, falhas suprimidas preservadas, rejeição de stage externo incompleto e transação aninhada.

### Teste de regressão
`synchronousOperationFailureRollsBackRestoresAndCloses`, `nullOperationStageRollsBackRestoresAndCloses`, `incompleteExternalStageIsRejectedAndConnectionIsReleased`, `commitFailureStillRestoresAndClosesConnection` e `rollbackFailureIsSuppressedOnOriginalFailure`.

### Compatibilidade
Callbacks transacionais devem concluir apenas trabalho SQL no mesmo executor; I/O externo deve ocorrer antes/depois da transação.

## [P1] Lifecycle de storage aceitava estados parciais

### Local
- módulo: storage
- arquivo: `CotaniStorage.java`
- linhas: 109-178, 206-331, 364-371
- método: `startAsync`/`closeAsync`/`LifecycleState`

### Problema
Falha em migration/repository podia deixar provider/executor reutilizável ou aberto; start/close concorrentes não tinham uma conclusão única e operações podiam entrar após fechamento.

### Cenário de falha
Duas threads iniciam; a migration falha depois de abrir SQLite; nova tentativa usa executor encerrado ou mantém lock no arquivo.

### Impacto
Recurso vazado e comportamento terminal imprevisível.

### Causa raiz
Booleans implícitos em vez de máquina de estados e cleanup idempotente.

### Correção proposta
Máquina `NEW/STARTING/RUNNING/CLOSING/CLOSED/FAILED`, futures coalescidos, executor público guardado e fechamento único de provider/executor.

### Teste de regressão
`concurrentStartCallsShareTheSameStartup`, `failedRepositoryRegistrationIsTerminalAndCloseRemainsIdempotent`, `migrationFailureClosesSQLiteProvider` e `closeAsyncIsIdempotentAndRejectsLaterQueries`.

### Compatibilidade
Instância em `FAILED` é terminal; criar nova instância para nova tentativa.

## [P1] CacheEntry e dirtyCount não eram linearizáveis

### Local
- módulo: cache
- arquivo: `CacheEntry.java`, `CaffeineDataCache.java`
- linhas: 50-121; 57-76, 323-455
- método: `update`/`mutate`/dirty tracking

### Problema
Mutator com efeito colateral dentro de atualização CAS podia executar várias vezes; contagem por chave confundia entradas antigas e novas durante remoção.

### Cenário de falha
Milhares de updates concorrentes reavaliam o mutator; listener de remoção da entrada antiga reduz o contador da entrada nova.

### Impacto
Valor incorreto, `dirtyCount` divergente/negativo e save omitido.

### Causa raiz
Função impura em CAS e ausência de identidade da geração contabilizada.

### Correção proposta
Serializar mutação por entrada e contabilizar a instância exata em mapa concorrente com remoção condicional.

### Teste de regressão
`mutableUpdaterExecutesExactlyOncePerConcurrentOperation`, `concurrentUpdatesPreserveDirtyCount`, `putOverwritingDirtyEntryDecrementsDirtyCount` e testes de mark/update repetido.

### Compatibilidade
Sem mudança de assinatura; mutator passa a executar exatamente uma vez por operação aceita.

## [P1] Save antigo do cache podia sobrescrever geração nova

### Local
- módulo: cache
- arquivo: `CaffeineDataCache.java`
- linhas: 455-557
- método: `enqueueSave`/`SaveLane`

### Problema
Versões eram comparáveis apenas dentro da mesma `CacheEntry`; saves de eviction e nova entrada podiam concluir fora de ordem.

### Cenário de falha
Entrada A suja é evictada e seu save atrasa; entrada B da mesma chave salva; A conclui por último e sobrescreve B no repository.

### Impacto
Perda persistente de dados.

### Causa raiz
Ausência de geração monotônica global por chave e serialização de writes.

### Correção proposta
`SaveOrder(generation, version)` e lane sequencial por chave; falha velha não substitui pending save novo.

### Teste de regressão
`oldEvictionSaveCannotOverwriteNewerEntrySave`, `oldSaveDoesNotClearNewerUpdate` e `failedEvictSaveIsRetriedOnClose`.

### Compatibilidade
Sem mudança pública; gravações da mesma chave são ordenadas.

## [P1] Fechamento do cache podia perder callbacks e bloquear a thread Paper

### Local
- módulo: cache
- arquivo: `CaffeineDataCache.java`
- linhas: 261-310, 486-498
- método: `closeAsync`/`close`

### Problema
O close não aguardava todo removal callback/lane e o adaptador síncrono podia bloquear o game loop.

### Cenário de falha
Invalidate dispara save assíncrono; close encerra antes do callback e dados dirty não chegam ao repository.

### Impacto
Perda de dirty data e stall no shutdown.

### Causa raiz
Callbacks não rastreados e close síncrono como caminho principal.

### Correção proposta
Rastrear eviction work, aguardar lanes/pending saves, coalescer `closeAsync`, rejeitar mutação e proibir `close()` na thread primária.

### Teste de regressão
`closeAsyncCancelsAutosaveAndSavesDirty`, `failedEvictSaveIsRetriedOnClose` e `closeAsyncIsCoalescedAndRejectsNewMutations`.

### Compatibilidade
`closeAsync` é canônico. `close()` permanece apenas para compatibilidade fora da thread do servidor.

## [P1] Sessão antiga de usuário podia sobrescrever reconnect

### Local
- módulo: user
- arquivo: `UserCache.java`, `SimpleUserService.java`
- linhas: 69-88; 42-218
- método: `updateIfSession`/`load`/`unload`/`persistSequentially`

### Problema
Loads eram removidos somente pela chave e unload/save antigo podia gravar ou remover a sessão nova; timeout tardio podia repopular cache limpo.

### Cenário de falha
Join A, quit A e join B ocorrem rapidamente; o save de A conclui após B e grava `lastQuitAt`/versão sobre B.

### Impacto
Sessão online perdida ou persistência regressiva.

### Causa raiz
`UUID` era a única versão; faltavam `sessionId`, geração de cache e lane de escrita.

### Correção proposta
Remoção condicional pelo future, update condicionado ao `sessionId`, geração no clear, timeout em cópia e serialização de saves por UUID.

### Teste de regressão
`oldSessionSaveCompletesBeforeNewSessionSave`, testes de `UserCache.updateIfSession`, load coalescido e cache clear com load tardio.

### Compatibilidade
Sem mudança de API; aumenta garantia de sessão.

## [P1] Destino alterado por pre-event não era revalidado

### Local
- módulo: teleport
- arquivo: `PaperTeleportService.java`
- linhas: 116-221
- método: `prepare`/`validateEventTarget`

### Problema
O evento podia trocar mundo/local depois de safety e policies, e o novo destino seguia direto para execução.

### Cenário de falha
Listener muda destino seguro para coordenada inválida, mundo descarregado ou região proibida; teleporte ainda ocorre.

### Impacto
Teleporte incorreto e bypass de proteção regional.

### Causa raiz
Validação aplicada ao destino inicial, não ao final.

### Correção proposta
Revalidar finitude/mundo/player, executar safe resolver e policies novamente após o evento.

### Teste de regressão
`eventChangedSafeTargetIsResolvedAndPoliciesAreRevalidated` e `eventChangedInvalidTargetIsRejectedBeforeTeleport`.

### Compatibilidade
Listeners que escolhem destino inválido agora recebem failure em vez de execução permissiva.

## [P1] Teleportes do mesmo jogador e timeout tinham resultado ambíguo

### Local
- módulo: teleport
- arquivo: `PaperTeleportService.java`
- linhas: 39-76, 222-277
- método: `teleport`/`executeTeleport`

### Problema
Duas requisições podiam atravessar cooldown/teleporte fora de ordem. Timeout do wrapper podia publicar falha e o `teleportAsync` real concluir depois com efeito no mundo.

### Cenário de falha
Pedido A expira no cliente; B começa; A completa fisicamente depois e move o jogador após B.

### Impacto
Posição/eventos/cooldown inconsistentes.

### Causa raiz
Ausência de pipeline por UUID e interpretação de timeout como cancelamento físico.

### Correção proposta
Serializar por jogador e, ao observar deadline, manter a lane ocupada até o future Paper original determinar o efeito real; finalizar uma vez no contexto da entidade.

### Teste de regressão
`teleportsForSamePlayerExecuteSequentially`, `asyncTimeoutWaitsForUnderlyingTeleportOutcome` e testes de completion no entity thread.

### Compatibilidade
Timeout deixa de prometer cancelamento que Paper não fornece; pode concluir mais tarde com o resultado real.

## [P1] Cache de subscriptions aceitava snapshot obsoleto

### Local
- módulo: event
- arquivo: `DefaultEventRegistry.java`
- linhas: 22-83
- método: `register`/`unregister`/`subscriptionsFor`

### Problema
`computeIfAbsent` concorrente podia inserir resolução antiga depois de `resolvedCache.clear()`.

### Cenário de falha
Publish resolve listeners enquanto subscribe registra novo listener; clear ocorre primeiro e a resolução antiga é publicada depois, omitindo o listener indefinidamente.

### Impacto
Eventos perdidos e contrato de subscribe violado.

### Causa raiz
Subscriptions e cache não pertenciam ao mesmo snapshot/versionamento.

### Correção proposta
Read/write lock envolvendo alteração, invalidação e resolução; listas permanecem imutáveis.

### Teste de regressão
`registrationCannotBeLostBehindConcurrentResolution` usa latch para fixar o interleaving.

### Compatibilidade
Sem mudança pública.

## [P1] Cooldown em cache separava check de acquire

### Local
- módulo: cooldown
- arquivo: `PlayerCooldowns.java`, `CacheCooldownStore.java`
- linhas: 47-65; 97-146
- método: `checkAndStart`

### Problema
Chamadas concorrentes podiam observar ausência e ambas adquirir o mesmo cooldown.

### Cenário de falha
Dois teleportes simultâneos leem cooldown vazio antes de qualquer put; ambos são permitidos.

### Impacto
Bypass de rate limit/cooldown.

### Causa raiz
Sequência find/put e mutações duplicadas não atômicas.

### Correção proposta
`compute` atômico por ação/chave, um único timestamp; relógio monotônico para memória e wall clock somente na persistência.

### Teste de regressão
`cachedPlayerCooldownCheckAndStartIsAtomic` valida 32 concorrentes, exatamente um permitido e um `markDirty`.

### Compatibilidade
Sem mudança pública; duração não positiva agora é rejeitada.

## [P1] Configuração síncrona podia fazer I/O no tick

### Local
- módulo: config
- arquivo: `CotaniConfigsBuilder.java`, `CotaniConfigs.java`, `CotaniConfig.java`, implementações default
- linhas: 59-105; APIs `load/reload/save`
- método: `load`/`reload`/`save`

### Problema
APIs síncronas públicas não impediam leitura/gravação YAML na thread primária.

### Cenário de falha
Comando de reload chama `configs.reload()`; arquivo grande e parsing bloqueiam o tick.

### Impacto
Lag relevante da thread principal.

### Causa raiz
Só existia `reloadAsync` no agregado; arquivo individual e save não tinham alternativa completa nem guarda.

### Correção proposta
Adicionar `reloadAsync`/`saveAsync` ao agregado e arquivo, executar no scheduler explícito e rejeitar o caminho síncrono na thread primária.

### Teste de regressão
`CotaniConfigsBuilderTest` valida agendamento/completion; o check compila todos os novos contratos.

### Compatibilidade
Adição abstrata a `CotaniConfig`/`CotaniConfigs` é incompatível para implementadores externos. Migração: implementar os dois métodos via executor explícito; chamadas sync só fora da thread Paper.

## [P1] Prometheus era público por padrão e o close podia esperar no game loop

### Local
- módulo: metrics
- arquivo: `MetricsConfig.java`, `PrometheusServer.java`, `CotaniMetricsModule.java`
- linhas: 10-39; 38-103; 68-72
- método: `create`/`start`/`handleScrape`/`close`

### Problema
`new InetSocketAddress(port)` escutava em todas as interfaces, o contexto aceitava prefixos e close aguardava até três segundos.

### Cenário de falha
Servidor abre `/metrics` na interface pública sem intenção; `/metrics/private` também cai no handler; shutdown ocorre na thread principal.

### Impacto
Exposição de telemetria e stall de lifecycle.

### Causa raiz
Ausência de host seguro/default e shutdown com delay/await.

### Correção proposta
Default `127.0.0.1`, caminho exato, porta dinâmica testável, `stop(0)`/`shutdownNow` e ausência modelada por `Optional`.

### Teste de regressão
`enabledModuleCreatesActiveRegistryAndServer`, `disabledModuleUsesNoOpRegistry` e `prometheusServerRespondsToHttpGet` (inclui 404 para subpath).

### Compatibilidade
`MetricsConfig` ganhou componente `host`, preservando construtor antigo. `prometheusServer()` mudou de nullable para `Optional`; consumidores usam `orElseThrow`/`ifPresent`.

## [P2] Batch reutilizava parâmetros e coleção mutável do chamador

### Local
- módulo: storage
- arquivo: `QueryExecutor.java`
- linhas: 89-95, 195-227
- método: `batch`/`runBatch`

### Problema
Binder com menos parâmetros podia herdar valores, a lista podia mudar depois do agendamento e blocos de mil não limpavam o batch explicitamente.

### Cenário de falha
Chamador altera binders enquanto o executor inicia; linha seguinte recebe parâmetro residual.

### Impacto
Dados incorretos e comportamento dependente do driver.

### Causa raiz
Falta de snapshot, `clearParameters` e `clearBatch`.

### Correção proposta
`List.copyOf` antes do async, clear antes de cada binder e após cada bloco; resultados de query em lista imutável.

### Teste de regressão
`batchCopiesCallerListAndClearsParametersBetweenBinders`, `batchClearsDriverBatchAfterEachThousandRows` e `queryManyReturnsImmutableList`.

### Compatibilidade
Sem quebra; mutação tardia deixa de afetar a operação.

## [P2] Timeout JDBC e pool aceitavam valores inconsistentes

### Local
- módulo: storage
- arquivo: `CotaniStorageBuilder.java`, `MySqlCredentials.java`
- linhas: 89-127; 12-105
- método: `queryTimeout`/`toQueryTimeoutSeconds`/`PoolSettings`

### Problema
Timeout positivo subsegundo virava zero (desativava timeout JDBC); overflow virava inteiro inválido; pool aceitava `minimumIdle > maximumPoolSize` e durations impróprias.

### Cenário de falha
`Duration.ofMillis(500)` configura `Statement.setQueryTimeout(0)` e a query fica sem deadline.

### Impacto
Edge case de disponibilidade e bootstrap.

### Causa raiz
Cast truncado para segundos e validação incompleta.

### Correção proposta
Arredondar para cima, rejeitar além de `Integer.MAX_VALUE`, validar pool/host/durations e formar IPv6/DB URL corretamente.

### Teste de regressão
`positiveSubsecondTimeoutRoundsUpInsteadOfDisablingJdbcTimeout`, `timeoutBeyondJdbcIntegerRangeIsRejected`, `buildsIpv6UrlWithoutFormEncodingTheHost` e `rejectsInvalidPoolBoundsAndDurations`.

### Compatibilidade
Configurações antes aceitas mas inválidas agora falham no construtor.

## [P2] Caminhos de config permitiam symlink escape e YAML sem limite

### Local
- módulo: config
- arquivo: `ConfigPaths.java`, `PathSerializer.java`, `BukkitYamlConfigSource.java`
- linhas: 10-50; 18-31; 20, 53-61
- método: `requireContained`/`loadYaml`

### Problema
`normalize().startsWith` não impedia symlink para fora e arquivos arbitrariamente grandes eram enviados ao parser.

### Cenário de falha
`plugins/Cotani/link.yml` aponta para fora do data folder; serializer resolve o link ou YAML multi-megabyte pressiona heap/CPU.

### Impacto
Escrita/leitura fora do diretório e DoS local.

### Causa raiz
Validação somente lexical e ausência de limite.

### Correção proposta
Rejeitar qualquer componente symlink, validar containment por real path de forma fail-closed e limitar arquivo a 4 MiB antes do parse.

### Teste de regressão
`PathSerializerTest` cobre traversal/symlink; `rejectsOversizedFileBeforeYamlParsing` cobre tamanho.

### Compatibilidade
Symlinks de configuração deixam de ser suportados deliberadamente.

## [P2] Limites e escala eram globais em economia multimoeda

### Local
- módulo: economy
- arquivo: `CurrencyDefinition.java`, `EconomySettings.java`, `DefaultEconomyGuard.java`
- linhas: 7-59; 12-224; 24-61
- método: `requireEnabledDefinition`/validação de amount

### Problema
Operação em moeda não padrão usava casas e limites da moeda padrão.

### Cenário de falha
Moeda de quatro casas é validada como duas, ou usa máximo de uma moeda diferente.

### Impacto
Rejeição/aceitação incorreta e precisão inconsistente.

### Causa raiz
Settings possuía apenas limites globais.

### Correção proposta
Mapear `CurrencyId -> CurrencyDefinition` com escala, saldo inicial, máximos, mínimo de pay e enabled; fallback global só para construtor legado.

### Teste de regressão
`DefaultEconomyGuardTest` cobre escalas 0/2/4, limites distintos, desconhecida e desabilitada.

### Compatibilidade
`EconomySettings` ganhou componente de record; construtor antigo permanece. Código que depende da forma/reflection/equals do record deve migrar para o novo componente.

## [P2] Close do container não era coalescido

### Local
- módulo: core
- arquivo: `Cotani.java`
- linhas: 88-160
- método: `closeAsync`/`close`

### Problema
Chamadas concorrentes podiam iniciar fechamentos repetidos e o adaptador síncrono não protegia a thread primária.

### Cenário de falha
Disable e watchdog pedem close juntos; um recurso recebe duas chamadas enquanto outro ainda fecha.

### Impacto
Lifecycle inconsistente.

### Causa raiz
Faltava future atômico compartilhado.

### Correção proposta
CAS de `closeFuture`, composição única, guarda Paper e preservação de interrupção no adaptador legado.

### Teste de regressão
Teste `CotaniTest` de coalescimento concorrente.

### Compatibilidade
`closeAsync` é o caminho recomendado; `close()` bloqueante só fora da thread do servidor.

## [P2] Entradas públicas de payload/textura/template eram ilimitadas

### Local
- módulo: task, text, item
- arquivo: `PersistentTask.java`, `FilePersistentTaskStore.java`, `MiniMessages.java`, `SkullTextureResolver.java`
- linhas: validação dos records/load; 20-43/193-204; 27-66
- método: construtores/parsers/resolvers

### Problema
Payload persistente, nome, MiniMessage, base64 e URL podiam reter/decodificar entradas muito grandes.

### Cenário de falha
Entrada pública cria centenas de templates de 10 MiB ou um arquivo de tarefa com payload gigante.

### Impacto
Pressão de heap/disco e CPU.

### Causa raiz
Caches eram limitados por quantidade, não por tamanho de cada chave/valor.

### Correção proposta
Limites explícitos: payload 1 MiB, nome 128/single-line, template 32 KiB, base64 16 KiB e URL 2 KiB; cópia defensiva do byte array e Base64 validado.

### Teste de regressão
`PersistentTaskTest`, `rejectsOversizedTemplatesBeforeParsingOrCaching` e testes de URL/base64 oversized.

### Compatibilidade
Entradas acima dos limites agora falham cedo com `IllegalArgumentException`.

## [P2] Baseline/build não era reproduzível

### Local
- módulo: build/documentação
- arquivo: `libs.versions.toml`, workflows, `build.gradle.kts`, README/CONTRIBUTING
- linhas: versões e configuração Spotless
- método: toolchain/CI/formatter

### Problema
Java 21 era anunciado/usado na CI enquanto toolchain era 25; Paper era RC; links `file:///D:/...` não funcionavam; Palantir 2.94 falhava no check multimódulo JDK 25.

### Cenário de falha
Contributor usa Java 21 conforme docs e não compila; CI baixa toolchain implicitamente; formatter quebra de modo intermitente antes dos testes.

### Impacto
Build não reproduzível e documentação enganosa.

### Causa raiz
Fontes de versão divergentes e formatter desatualizado.

### Correção proposta
Unificar Java 25/Paper stable, atualizar workflows/links/claims e Palantir 2.96.0.

### Teste de regressão
`clean check`, `build` e `validateModuleArchitecture` finais.

### Compatibilidade
Java 21/Paper 1.20 deixam de ser suportados pela linha atual; consumidores antigos precisam permanecer em release anterior.

## [P3] Semântica do EventBus era implícita

### Local
- módulo: event
- arquivo: `EventBus.java`
- linhas: 6-30
- método: `publish`/`publishAsync`

### Problema
Ordem, recursão, unsubscribe durante dispatch, `Error`, listener bloqueante, cancelamento e mutação async não estavam documentados.

### Cenário de falha
Consumidor assume isolamento de `Error` ou copia evento automaticamente no publish async.

### Impacto
Uso ambíguo e manutenção difícil.

### Causa raiz
Contrato mínimo sem Javadoc comportamental.

### Correção proposta
Documentar ordem estável por prioridade/registro, snapshot ativo, recursão imediata, propagação de Error, blocking e responsabilidade de thread/cancelamento.

### Teste de regressão
Testes existentes de prioridade/cancelamento mais o teste concorrente do registry.

### Compatibilidade
Apenas documentação; sem mudança de execução.

## [P3] Claims e links da documentação não refletiam o código

### Local
- módulo: documentação
- arquivo: README, CONTRIBUTING, templates GitHub
- linhas: badges, overview, matrix e links
- método: n/a

### Problema
README omitia módulos, prometia ausência absoluta de estado estático/bloqueio e continha links locais.

### Cenário de falha
Consumidor segue link `D:/Cotani` ou interpreta adaptadores de lifecycle como pipeline sem bloqueio.

### Impacto
Onboarding e contrato público imprecisos.

### Causa raiz
Documentação não acompanhou o repositório.

### Correção proposta
Links relativos, 14 módulos e claims delimitados a flows de aplicação/estado controlado.

### Teste de regressão
Busca final, excluindo as menções históricas deste relatório, não encontra links `file:///`, baseline Java 21 ou suporte Paper 1.20 nos documentos ativos.

### Compatibilidade
Nenhuma.

## Riscos residuais corrigidos nesta rodada

## [P2] Cobertura real de MySQL e MariaDB

### Problema e impacto
SQLite não provava o SQL condicional, locking, isolamento e idempotência sob duas conexões nos outros dialetos.

### Correção implementada
Testcontainers 2.0.5 foi adicionado aos módulos economy e cooldown, com MySQL 8.4.6 e MariaDB 11.4.8 reais. As suites iniciam duas instâncias de `CotaniStorage` contra o mesmo banco e exercitam migrations, retry idempotente, withdrawals concorrentes e aquisição distribuída de cooldown.

### Testes
`MySqlMariaDbEconomyIntegrationTest` e `MySqlMariaDbDistributedCooldownIntegrationTest`. `disabledWithoutDocker = true` mantém o build local portável; os quatro métodos são executados automaticamente quando Docker está disponível.

### Compatibilidade
Somente dependências de teste; nenhuma alteração no runtime do consumidor.

## [P2] Backpressure alinhado ao pool SQL

### Problema e impacto
O executor virtual podia acumular uma quantidade ilimitada de operações aguardando poucas conexões Hikari, ampliando memória e latência de cauda.

### Correção implementada
`AdmissionControlledExecutorService` limita trabalho ativo e fila sem bloquear nem executar no chamador. SQLite usa concorrência 1; MySQL/MariaDB derivam o limite de `maximumPoolSize`; executores fixos usam o menor valor entre threads e pool. `admissionQueueCapacity` é configurável, rejeições viram stages falhos e `StorageExecutorStats` expõe pressão e total rejeitado.

### Testes
`AdmissionControlledExecutorServiceTest`, casos de rejeição/close em `CotaniStorageTest` e validação negativa do builder. A saturação prova que o chamador não bloqueia e que o pico não excede o limite.

### Compatibilidade
Sob overload, a falha agora é explícita. O padrão permanece 256 operações aguardando; consumidores podem ajustar a capacidade e observar métricas.

## [P2] Consistência de cache/economy e fan-out de saves

### Problema e impacto
Caches locais não observavam outra instância e flush de muitas chaves criava fan-out de futures; saldo econômico em cache podia ficar obsoleto entre servidores.

### Correção implementada
`CacheInvalidationBus` fornece contrato pub/sub opcional, com implementações noop e local. Writers participantes publicam após persistência; invalidação remota remove apenas entradas limpas e nunca descarta alteração dirty local. Bulk save/close usam coordenador assíncrono com concorrência configurável, padrão 16. O cache de saldo do economy foi removido: leitura e mutação usam a fonte SQL forte, e sua configuração agora pertence ao lifecycle Cotani.

### Testes
`CaffeineDataCacheCoordinationTest` cobre duas instâncias, proteção de dirty state e 10 mil saves com pico limitado. A suite economy de containers cobre duas instâncias sobre o mesmo banco.

### Compatibilidade
A configuração de cache de saldo foi removida por não ter efeito. Caches genéricos continuam locais/eventuais sem um bus compartilhado, agora de forma explícita no contrato.

## [P2] Cooldown atômico entre servidores e cleanup proprietário

### Problema e impacto
O check-and-start persistente era atômico apenas no processo; dois servidores podiam permitir a mesma ação, e churn de chaves expiradas aumentava cardinalidade.

### Correção implementada
`DistributedCooldownService` executa upsert condicional atômico em SQLite, MySQL e MariaDB, identifica o vencedor por lease token e oferece somente APIs `CompletionStage`. O serviço possui cleanup agendado; stores locais fazem limpeza oportunista e limitam tamanho de action/resource IDs. A leitura usa colunas canônicas, permitindo `:` em alvo/ação, e a remoção expirada é condicional para não apagar uma renovação concorrente.

Migrations passaram a ser versionadas por namespace. O histórico global legado é copiado por `(versão, descrição)` para `cotani_migrations_v2`, sem reexecutar migrations já aplicadas e sem colidir versões iguais de módulos diferentes.

### Testes
64 aquisições entre duas instâncias SQLite permitem exatamente uma; há churn/cleanup com clock falso, round-trip de delimitadores e suites equivalentes Testcontainers. `LegacyMigrationHistoryIntegrationTest` comprova upgrade sem reexecução.

### Compatibilidade
O serviço distribuído é aditivo. `Migration.namespace()` tem default pelo pacote; implementações podem sobrescrevê-lo para estabilidade em caso de reorganização futura.

## [P2] Paths/YAML e integrações sem conclusão

### Problema e impacto
Symlinks trocáveis, YAML sem orçamento próprio, future Paper sem conclusão e listener bloqueado permitiam escape de diretório ou retenção indefinida de pipelines.

### Correção implementada
Config/storage rejeitam symlink em todos os componentes, abrem arquivos com `NOFOLLOW_LINKS`, limitam YAML a 4 MiB/50 mil linhas/nesting 64/50 aliases, revalidam file key/tamanho e salvam por temp+atomic move. SQLite pré-cria e revalida o arquivo antes/depois da abertura JDBC.

Teleporte aplica timeout de reconciliação: resultado nunca concluído retorna `OUTCOME_INDETERMINATE`, coloca o jogador em quarentena, reconcilia sucesso tardio na entity thread e fornece liberação administrativa explícita. `publishAsync` do event bus isola listeners em virtual threads, aplica deadline público, interrompe e opcionalmente desinscreve o listener lento; o bus fecha seu executor.

### Testes
Cobertura de YAML grande/profundo/aliases, symlink de config/storage, future Paper que nunca conclui, reconciliação tardia, quarentena/liberação e listener bloqueado com continuação do próximo listener.

### Compatibilidade
Construtores antigos de teleporte foram preservados com reconciliação padrão de 30 segundos. O limite de listener async padrão é cinco segundos. Em Java portável não existe `openat` para manter um descritor de diretório até o JDBC; as revalidações fail-closed são complementadas por permissões de diretório no deployment.

## [P3] Superfície pública e estado global

### Problema e impacto
Getters nullable de `Row`, implementações públicas não demarcadas e resolver estático de skull aumentavam NPE, acoplamento e estado global entre plugins/testes.

### Correção implementada
`Row.getString` é non-null fail-fast; getters `Optional` são a única API para valores potencialmente ausentes. `SkullBuilder.create(resolver)` permite injeção e `create()` usa resolver uncached sem singleton estático. Declarações públicas em `impl/internal` são marcadas `@InternalApi`; `validateModuleArchitecture` falha para novas exposições não marcadas ou importadas entre módulos.

### Testes
`RowTest`, `SkullBuilderTest`/`SkullTextureResolverTest` e a validação arquitetural do build.

### Compatibilidade
Consumidores de valores nullable devem usar os getters `Optional`; a mudança elimina a superfície que permitia `null` inesperado.

## Mudanças de API e plano de migração

| Mudança | Impacto | Migração |
|---|---|---|
| `PaperTaskScheduler.closeAsync()` | adição abstrata para implementadores | implementar fechamento não bloqueante e coalescido; usar no lifecycle Paper |
| `CotaniConfig.reloadAsync/saveAsync` e `CotaniConfigs.saveAsync` | adição abstrata | executar I/O em executor explícito; não chamar sync na thread primária |
| `MetricsConfig.host` | forma do record mudou; construtor antigo preservado | usar construtor de 5 args para bind público; revisar reflection/destructuring |
| `CotaniMetricsModule.prometheusServer(): Optional` | source incompatible para quem esperava nullable | usar `ifPresent`, `orElseThrow` ou `isEmpty` |
| `EconomySettings.currencyDefinitions` | forma/equals do record mudou; overload antigo preservado | declarar uma `CurrencyDefinition` por moeda; fallback legado só para migração |
| retry de stage externo | mudança semântica | fornecer supplier repetível à origem da chain |
| saturação do task executor | agora rejeita | tratar `RejectedExecutionException` e aplicar admission policy explícita |
| `CotaniStorageBuilder.admissionQueueCapacity`/`executorStats` | adições; saturação SQL agora é observável | dimensionar fila pelo workload e tratar conclusão excepcional |
| `Migration.namespace()` | default aditivo; histórico passou a `(namespace, version)` | manter o pacote ou sobrescrever com identificador estável; histórico legado é backfilled automaticamente |
| `DataCacheBuilder.invalidationBus`/`maximumConcurrentSaves` | adições opcionais | fornecer bus compartilhado para múltiplas instâncias e ajustar concorrência de flush |
| `DistributedCooldownService` | nova API assíncrona | registrar `CotaniCooldowns.migrations()` e usar `CotaniCooldowns.distributed(...)` |
| `ExecutionSettings.reconciliationTimeout` | forma do record mudou; construtor antigo preservado | configurar deadline e tratar `OUTCOME_INDETERMINATE`/quarentena |
| `EventDispatchPolicy` e `EventBus.close()` | política/lifecycle aditivos | fechar o bus criado pela factory e dimensionar deadline de listener async |
| `SkullBuilder.create(SkullTextureResolver)` | overload aditivo | injetar resolver compartilhado quando cache por plugin for desejado |
| sync close/config I/O no servidor | agora rejeita | usar os métodos `*Async` e compor a conclusão |
| runtime Java/Paper | baseline elevado/explicitado | atualizar runtime para Java 25/Paper 26.2 ou permanecer em release anterior |

## Testes adicionados e cobertura

Foram adicionados 72 métodos de teste em relação ao `HEAD` original. Casos cobertos incluem timeout/falha original, retry real/cancelamento, debounce zero, transação sync/null/rollback/close, batch residual e >1000, lista imutável, mutator exatamente uma vez, dirty count, eviction vs geração nova, moedas 0/2/4, withdrawals concorrentes, idempotência persistente, cache de evento, destino alterado, teleportes simultâneos, cooldown atômico, reconnect/save antigo, close duplo, operação pós-close, startup parcial, backpressure SQL, invalidação entre caches, fan-out de 10 mil saves, YAML adversarial, symlink, listener bloqueado, teleporte sem conclusão, histórico legado de migrations e duas instâncias MySQL/MariaDB.

Distribuição final: cache 104, config 46, cooldown 10 (2 condicionados a Docker), core 23, economy 60 (2 condicionados a Docker), event 7, gui 21, item 12, metrics 10, storage 93, task 62, teleport 68, text 63 e user 31 testes.

## Varreduras finais

- Produção contém dois `Future.get(timeout)`: somente adaptadores legados `Cotani.close()` e `DataCache.close()`, ambos proibidos na thread primária, documentados e com caminho `closeAsync` canônico.
- Não há `join`, `Thread.sleep` ou `TimeUnit.sleep` em produção.
- Todos os `CompletableFuture.supplyAsync/runAsync` encontrados recebem executor explícito.
- `get()` restantes são Optional/Atomic/Map/Supplier, não bloqueio de future.
- `return null` restante está em adapters nullable explícitos, callbacks internos/`VoidResult`, não em novas APIs de ausência.
- Capturas de `Throwable` restantes existem em boundaries que precisam completar futures/cleanup; listeners de eventos capturam apenas `Exception` e deixam `Error` propagar conforme contrato.
- O cache estático de `MiniMessages` permanece limitado a 512; skull usa resolver por instância, injetável e pertencente ao chamador, sem cache global estático.

## Recomendações de benchmark

1. JMH de `DefaultEventRegistry.subscriptionsFor` com 1/10/100 tipos e churn de subscribe; comparar lock atual com snapshot versionado.
2. Stress de cache com 10k/100k chaves, eviction+save lento e limiter de persistência; medir futures, heap e p99 de close.
3. Testcontainers MySQL/MariaDB: throughput e p99 para pool 2/10/32, executor fixo vs virtual com admission gate.
4. Economia: duas instâncias, withdrawals/transfers cruzadas e retry após timeout do cliente.
5. MiniMessage/YAML: tamanhos próximos aos limites e profundidade adversarial, registrando CPU/allocations.
6. Teleporte: múltiplos jogadores e mesma entidade, chunk lento e future nunca concluído; medir tamanho das lanes.

## Conclusão

O repositório final compila e testa de forma reproduzível no baseline declarado. Os invariantes críticos agora são verificáveis: idempotência ligada à intenção, transação fechada em todos os caminhos, save por chave ordenado e limitado, dirty tracking por identidade, sessão versionada, timeout/retry sem contaminar futures, backpressure SQL, cooldown distribuído atômico, teleporte serializado com reconciliação e lifecycle coalescido. Todos os 33 achados foram encerrados; Docker e permissões de filesystem permanecem requisitos de infraestrutura para executar a cobertura multi-backend e reforçar o modelo de ameaça local.
