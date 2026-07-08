# cotani-user

User lifecycle and cache management service. Manages loading user profiles asynchronously on join, thread-safe online cache storage, and background database saves on leave.

## Usage

```java
UserService service = module.userService();

service.findAsync(playerId).thenAccept(maybeUser -> {
    maybeUser.ifPresent(user -> {
        // Access user properties
    });
});
```
