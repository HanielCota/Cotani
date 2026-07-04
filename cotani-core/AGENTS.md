# cotani-core

## Scope

Lifecycle management and shared contracts for the Cotani framework.

## Hard rules

1. `Cotani` must be created in the plugin `onEnable` and closed in `onDisable`.
2. Every `AutoCloseable` resource created at startup must be registered with `Cotani.forPlugin(plugin).with(resource).build()`.
3. Do not use `Cotani` as a service locator; it only manages closeables.
4. `CotaniCloseException` is the only exception thrown by `Cotani.close()`; log suppressed failures appropriately.

## Patterns

```java
Cotani cotani = Cotani.forPlugin(this)
    .with(scheduler)
    .with(storage)
    .with(configs)
    .build();
```

## Anti-patterns

- Holding resources in plugin fields without registering them in `Cotani`.
- Calling `cotani.close()` more than once intentionally; it is safe but usually indicates a design issue.

## Related skills

- `java-engineering-standards`
- `paper-plugin-architecture`
