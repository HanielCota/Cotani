# cotani-event

A lightweight, reflection-free Java Event Bus for asynchronous and synchronous event dispatching, prioritizations, and priority listeners.

## Usage

```java
EventBus eventBus = DefaultEventBus.createDefault();

EventSubscription sub = eventBus.subscribe(UserLoadedEvent.class, event -> {
    System.out.println("User loaded: " + event.userId());
});

eventBus.publish(new UserLoadedEvent(userId));
sub.unsubscribe();
```
