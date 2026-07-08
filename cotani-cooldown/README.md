# cotani-cooldown

Thread-safe, non-blocking cooldown manager. Tracks actions, remaining duration, and persistent cooldown states.

## Usage

```java
CooldownService cooldowns = DefaultCooldownService.inMemory();

CooldownResult result = cooldowns.user(userId)
    .action("daily.reward")
    .duration(Duration.ofHours(24))
    .checkAndStart();

if (result.denied()) {
    Duration remaining = result.remaining();
    // Deny access
}
```
