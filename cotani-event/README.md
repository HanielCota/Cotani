# cotani-event

Módulo de eventos interno da API Cotani.

## Objetivo

Este módulo fornece um Event Bus Java puro para comunicação entre módulos, sem dependência de Bukkit, comandos ou menus.

## Exemplo

```java
EventBus eventBus = DefaultEventBus.createDefault();

EventSubscription subscription = eventBus.subscribe(UserLoadedEvent.class, event -> {
    System.out.println("User loaded: " + event.userId());
});

eventBus.publish(new UserLoadedEvent(UUID.randomUUID()));
subscription.unsubscribe();
```

## Princípios

- Java puro
- SRP
- Sem reflection
- Sem Bukkit
- Síncrono por padrão
- Async explícito via `publishAsync`
- Listeners com prioridade
- Suporte a eventos canceláveis
- Tratamento explícito de exception
- Thread-safe

## Java

Recomendado: Java 17+
