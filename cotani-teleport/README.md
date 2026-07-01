# Cotani Teleport

Módulo moderno de teleport para Paper, pensado para a Cotani API.

## Stack

- Java 25
- Gradle Kotlin DSL
- Paper API `26.1.2.build.+`
- Adventure Component API
- `CompletableFuture<TeleportResult>`
- `record`, `sealed interface`, `switch pattern matching`
- Teleport assíncrono por padrão via `Player#teleportAsync`

## Principais padrões aplicados

- Facade: `TeleportService`, `PendingTeleportService`
- Builder: `TeleportRequest`, `TeleportOptions`
- Strategy: `TeleportPolicy`, `SafeLocationResolver`
- Chain of Responsibility: `TeleportPolicyChain`
- Observer/Event: eventos Bukkit próprios da Cotani
- Command-like pending teleport: `PendingTeleport`
- Adapter: `CombatAdapter`, `RegionProtectionAdapter`
- Result Object: `TeleportResult.Success` / `TeleportResult.Failure`

## Build

```bash
./gradlew build
```

O jar será gerado em:

```text
build/libs/cotani-teleport-1.0.0.jar
```

## Uso direto

```java
CotaniTeleport.teleports().teleport(
        TeleportRequest.builder()
                .player(player)
                .target(location)
                .cause(TeleportCause.SPAWN)
                .source("cotani-spawn")
                .options(TeleportOptions.spawn())
                .build()
).thenAccept(result -> switch (result) {
    case TeleportResult.Success success ->
            player.sendMessage(Component.text("Teleportado com sucesso."));

    case TeleportResult.Failure failure ->
            player.sendMessage(Component.text("Falha no teleport: " + failure.reason()));
});
```

## Teleport com delay

```java
CotaniTeleport.pendingTeleports().schedule(
        player,
        location,
        Duration.ofSeconds(5),
        TeleportOptions.spawn(),
        TeleportCause.SPAWN,
        "cotani-spawn"
);
```

O listener padrão cancela teleport pendente quando o jogador:

- muda de bloco;
- toma dano;
- sai do servidor.

## Pontos de integração

Substitua os adapters `noop` por integrações reais:

```java
new CombatTeleportPolicy(new SeuCombatAdapter())
new RegionTeleportPolicy(new SeuWorldGuardAdapter())
```

## Observação

O projeto está estruturado para servir como módulo base da Cotani. Ele compila contra Paper API `26.1.2.build.+`, conforme coordenada publicada pelo projeto PaperMC. Ajuste a versão no `build.gradle.kts` se o seu ambiente usar outro build.
