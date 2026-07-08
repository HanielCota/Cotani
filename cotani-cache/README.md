# cotani-cache

Caffeine-backed asynchronous caching. Provides generic `DataCache` and player-focused `PlayerDataCache` featuring automatic dirty tracking, presets, and background saves.

## Usage

```java
PlayerDataCache<User> users = CotaniCache.players(User.class)
    .repository(userRepository)
    .preset(CachePreset.PLAYER_DATA)
    .build(plugin, scheduler);

// Mutate data asynchronously
users.mutateAsync(playerId, user -> user.addCoins(100));
```
