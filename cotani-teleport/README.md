# cotani-teleport

Módulo moderno de teleport para Paper. Oferece teleport assíncrono, teleports pendentes com delay, políticas de validação (combat, cooldown, permissão, região), safe-location e eventos próprios.

## Responsabilidade

- Executar teleports via `Player#teleportAsync` quando possível.
- Oferecer teleports pendentes (com delay) canceláveis por movimento, dano ou quit.
- Validar teleports através de uma cadeia de políticas configurável.
- Resolver localizações seguras para teleport.
- Publicar eventos Bukkit próprios (`CotaniPreTeleportEvent`, `CotaniPostTeleportEvent`, `CotaniTeleportFailEvent`).
- Permitir adapters para integração com sistemas de combate e proteção de região.

## Stack

- Java 21+
- Paper API
- Adventure Component API
- JSpecify
- `cotani-core`, `cotani-task`

## Uso básico

```java
TeleportModule module = CotaniTeleports.create(plugin, scheduler);
TeleportService teleports = module.teleportService();

teleports.teleport(
    TeleportRequest.builder()
        .playerId(player.getUniqueId())
        .target(location)
        .cause(TeleportCause.SPAWN)
        .source("cotani-spawn")
        .options(TeleportOptions.spawn())
        .build()
).thenAccept(result -> switch (result) {
    case TeleportResult.Success success ->
        player.sendMessage(Component.text("Teleportado com sucesso."));
    case TeleportResult.Failure failure ->
        player.sendMessage(Component.text("Falha: " + failure.reason()));
});
```

## Teleport com delay

```java
PendingTeleportService pending = module.pendingTeleportService();

pending.schedule(
    TeleportRequest.builder()
        .playerId(player.getUniqueId())
        .target(location)
        .cause(TeleportCause.HOME)
        .source("home")
        .options(TeleportOptions.builder()
            .checkCooldown(true)
            .cooldownDuration(Duration.ofMinutes(5))
            .build())
        .build(),
    Duration.ofSeconds(5)
);
```

O listener padrão cancela teleport pendente quando o jogador:

- muda de bloco;
- toma dano;
- sai do servidor.

## Opções de teleport

```java
TeleportOptions options = TeleportOptions.builder()
    .async(true)
    .safeLocation(true)
    .checkCombat(true)
    .checkCooldown(true)
    .checkPermission(true)
    .checkRegion(true)
    .preserveVelocity(false)
    .dismount(true)
    .closeInventory(true)
    .playEffects(true)
    .sendMessages(true)
    .timeout(Duration.ofSeconds(10))
    .build();
```

Presets disponíveis:

- `TeleportOptions.defaults()`
- `TeleportOptions.spawn()`
- `TeleportOptions.admin()` — ignora combat, cooldown, permissão e região.
- `TeleportOptions.silent()` — sem mensagens/efeitos.

## Adapters

Substitua os adapters `noop` por integrações reais:

```java
CombatAdapter combat = player -> seuCombatManager.isInCombat(player);
RegionProtectionAdapter region = (player, target) -> seuProtection.canBuild(player, target);

TeleportModule module = CotaniTeleports.create(plugin, combat, region, scheduler);
```

## Eventos

| Evento | Momento |
|--------|---------|
| `CotaniPreTeleportEvent` | Antes do teleport ser executado. |
| `CotaniPostTeleportEvent` | Após teleport bem-sucedido. |
| `CotaniTeleportFailEvent` | Quando o teleport falha. |

## API pública

| Classe/Interface | Descrição |
|------------------|-----------|
| `CotaniTeleports` | Fachada estática para criar o módulo. |
| `TeleportModule` | Lifecycle handle: expõe `TeleportService`, `PendingTeleportService`, `TeleportCooldownService`, opções e scheduler. |
| `TeleportService` | Executa teleports imediatos. |
| `PendingTeleportService` | Agenda teleports com delay. |
| `TeleportRequest` | Record com builder para requisições de teleport. |
| `TeleportOptions` | Configurações de execução, segurança, políticas, jogador, feedback e timeout. |
| `TeleportResult` | `sealed interface` com `Success` e `Failure`. |
| `TeleportCause` / `TeleportFailureReason` / `TeleportCancelReason` | Enumerações de domínio. |
| `TeleportPolicy` / `TeleportPolicyChain` | Cadeia de políticas de validação. |
| `CombatTeleportPolicy` / `CooldownTeleportPolicy` / `PermissionTeleportPolicy` / `RegionTeleportPolicy` | Políticas embutidas. |
| `SafeLocationResolver` / `BlockSafetyChecker` / `SafeLocationOptions` | Resolução de local seguro. |
| `CombatAdapter` / `RegionProtectionAdapter` | Adapters de integração. |
| `TeleportCooldownService` | Gerenciamento de cooldowns. |
| `PendingTeleportView` / `PendingTeleportState` / `PendingTeleportData` | Estados de teleport pendente. |
| `CotaniPreTeleportEvent` / `CotaniPostTeleportEvent` / `CotaniTeleportFailEvent` | Eventos Bukkit. |

## Estrutura de pacotes

```text
com.cotani.teleport
├── CotaniTeleports.java
├── api
│   ├── TeleportModule.java
│   ├── TeleportService.java
│   ├── PendingTeleportService.java
│   ├── TeleportRequest.java
│   ├── TeleportOptions.java
│   ├── TeleportResult.java
│   ├── TeleportResults.java
│   ├── TeleportCause.java
│   ├── TeleportFailureReason.java
│   ├── TeleportCancelReason.java
│   ├── TeleportMessages.java
│   ├── TeleportContext.java
│   ├── PendingTeleportView.java
│   ├── PendingTeleportState.java
│   ├── SafetySettings.java
│   ├── PolicySettings.java
│   ├── ExecutionSettings.java
│   ├── PlayerSettings.java
│   ├── FeedbackSettings.java
│   └── SafeLocationOptions.java
├── impl
│   ├── DefaultTeleportModule.java
│   ├── PaperTeleportService.java
│   ├── TeleportValidator.java
│   ├── TeleportResultMapper.java
│   ├── TeleportEventNotifier.java
│   └── CotaniTeleportPlugin.java
├── policy
│   ├── TeleportPolicy.java
│   ├── TeleportPolicyChain.java
│   ├── CombatTeleportPolicy.java
│   ├── CooldownTeleportPolicy.java
│   ├── PermissionTeleportPolicy.java
│   ├── RegionTeleportPolicy.java
│   ├── TeleportCooldownService.java
│   └── PolicyResult.java
├── pending
│   ├── DefaultPendingTeleportService.java
│   ├── PendingTeleportData.java
│   ├── PendingTeleportStateMachine.java
│   └── PendingTeleportListener.java
├── safety
│   ├── SafeLocationResolver.java
│   ├── DefaultSafeLocationResolver.java
│   └── BlockSafetyChecker.java
├── adapter
│   ├── CombatAdapter.java
│   └── RegionProtectionAdapter.java
├── event
│   ├── TeleportEventBus.java
│   ├── CotaniPreTeleportEvent.java
│   ├── CotaniPostTeleportEvent.java
│   └── CotaniTeleportFailEvent.java
├── config
│   ├── TeleportConfiguration.java
│   └── TeleportOptionsFactory.java
└── util
    └── LocationUtils.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":teleport"))
}
```

## Integração

- Requer `cotani-task` para scheduling assíncrono.
- Implementações de `CombatAdapter` e `RegionProtectionAdapter` conectam o módulo a plugins de combate e proteção.
