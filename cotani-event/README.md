# cotani-event

A lightweight, reflection-free Java Event Bus for asynchronous and synchronous event dispatching, prioritizations, and priority listeners.

## Overview

`cotani-event` provides an optimized, reflection-free, compile-time-friendly Event Bus implementation. It serves as an internal communication channel between decoupled systems in Paper/Folia plugins. By avoiding runtime reflection, it eliminates performance bottlenecks commonly associated with traditional event systems.

## Features

- **Reflection-Free Execution**: Fast invocation paths using functional interfaces rather than reflection.
- **Priority Rules**: Supports execution order prioritization (`LOWEST`, `LOW`, `NORMAL`, `HIGH`, `HIGHEST`).
- **Subscription Lifecycle**: Unsubscribe easily by invoking `.unsubscribe()` on the returned `EventSubscription`.
- **Cancellable Events**: Fully supports event cancellation paradigms through `CancellableEvent` and `AbstractCancellableEvent`.
- **Asynchronous & Synchronous Dispatch**: Dispatch events synchronously on the calling thread or asynchronously via executors.
- **Robust Exception Handling**: Plug-in custom handlers (`EventExceptionHandler`) to catch and log failures during listener execution.

## Usage

### 1. Basic Subscribe and Publish

Register listeners and publish event data:

```java
EventBus eventBus = DefaultEventBus.createDefault();

// Subscribe to a custom event
EventSubscription subscription = eventBus.subscribe(UserLoadedEvent.class, event -> {
    System.out.println("User loaded: " + event.userId());
});

// Publish the event to all listeners
eventBus.publish(new UserLoadedEvent(userId));

// Unsubscribe when the listener is no longer needed
subscription.unsubscribe();
```

### 2. Prioritized Subscriptions

Declare execution priority to control listener execution order:

```java
// Execution priority: HIGHEST runs first, LOWEST runs last
eventBus.subscribe(UserLoadedEvent.class, EventPriority.HIGH, event -> {
    // Executes before normal priority listeners
});
```

### 3. Cancellable Events

Create and publish cancellable events:

```java
public final class UserChatEvent extends AbstractCancellableEvent implements CotaniEvent {
    private final UUID userId;
    private final String message;

    public UserChatEvent(UUID userId, String message) {
        this.userId = userId;
        this.message = message;
    }
    
    // inherits setCancelled(boolean) and isCancelled()
}

// In the listener:
eventBus.subscribe(UserChatEvent.class, event -> {
    if (containsProfanity(event.message())) {
        event.setCancelled(true);
    }
});

// When publishing:
UserChatEvent event = new UserChatEvent(userId, message);
eventBus.publish(event);

if (event.isCancelled()) {
    // Abort action
}
```

## Hard Rules & Best Practices

1. **Explicit Unregistration**: Always release long-lived subscriptions by calling `.unsubscribe()` on the `EventSubscription` to avoid memory leaks.
2. **Safe Exception Handling**: Avoid swallowing exceptions within listeners. Define an `EventExceptionHandler` or handle exceptions locally in the handler logic.
3. **No Heavy Work in Sync Listeners**: Do not execute blocking database or network calls inside synchronous event listener callbacks. Delegate those tasks asynchronously.

## Anti-Patterns

- Forgetting to unsubscribe static or global context listeners when reloading plugins.
- Parsing and validating raw values inside listeners instead of using structured, immutable event payloads.
- Raising events recursively within their own event handlers (causing infinite loops/stack overflow).
