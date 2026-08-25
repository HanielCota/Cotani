---
id: quickstart
title: Quickstart de um plugin
sidebar_label: Quickstart
description: Crie um plugin mínimo com execução assíncrona segura.
---

# Quickstart de um plugin

Este guia mostra o fluxo recomendado: evento na thread proprietária, captura de valores imutáveis, operação assíncrona
e retorno à thread da entidade antes de tocar em APIs Paper.

## 1. Declare o plugin

```yaml
name: CotaniQuickStart
version: 1.0.0
main: com.example.cotaniquickstart.CotaniQuickStartPlugin
api-version: '1.21'
```

## 2. Crie o bootstrap

```java
public final class CotaniQuickStartPlugin extends JavaPlugin {
    private Cotani lifecycle;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        lifecycle = Cotani.forPlugin(this)
                .withAsync(scheduler::closeAsync)
                .build();
    }
}
```

## 3. Faça a transição async → entity thread

```java
public void showMessage(UUID playerId, PaperTaskScheduler scheduler) {
    scheduler.entity("show-message", playerId, () -> {
        var player = Bukkit.getPlayer(playerId);

        if (player != null) {
            player.sendMessage(Component.text("Cotani está executando na entity thread."));
        }
    });
}
```

Em código de produção, prefira um serviço de mensagens e não capture `Player` dentro de uma operação assíncrona.
Capture o `UUID`, carregue os dados e re-resolva o jogador na transição para a entity thread.

## 4. Compile os exemplos

O repositório possui exemplos em [`docs-examples`](https://github.com/HanielCota/Cotani/tree/master/docs-examples). Execute:

```bash
./gradlew :examples:compileJava
```

Para entender o fluxo completo, consulte o [Cookbook](ai/cotani-cookbook.md).
