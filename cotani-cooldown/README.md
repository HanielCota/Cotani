# cotani-cooldown

Módulo base de cooldown para o Cotani.

## Requisitos

- Java 17+
- Sem dependência Bukkit/Paper no core

## Uso básico

```java
CooldownService cooldownService = DefaultCooldownService.inMemory();

CooldownResult result = cooldownService
    .user(userId)
    .action("reward.daily")
    .duration(Duration.ofHours(24))
    .checkAndStart();

if (result.denied()) {
    Duration remaining = result.remaining();
    return;
}
```

## Uso curto

```java
if (cooldownService.deny(userId, "teleport.spawn", Duration.ofSeconds(5))) {
    return;
}
```
