# cotani-npc

## Scope

Packet-based virtual player NPC creation, tracking, look-at targeting, skin textures, equipment, and player interaction module for Paper and Folia.

## Hard rules

1. Register `CotaniNpcs.create(plugin, scheduler)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Construct immutable NPC definitions using `Npc.builder()` or `npcModule.create(...)`.
3. Never block the main thread or scheduler threads inside NPC interaction handlers (`NpcInteractEvent`).
4. Always escape player input before interpolating into NPC names or dialogs.

## Patterns

### Spawning an NPC with Skin and Interaction

```java
var npc = npcModule.create(builder -> builder
    .location(spawnLocation)
    .name("<gold><bold>Quest Master</bold></gold>")
    .skin(textureValue, textureSignature)
    .lookAtPlayer(true)
    .onInteract(event -> {
        var player = event.player();
        if (event.action() == NpcInteractEvent.Action.RIGHT_CLICK) {
            player.sendMessage(MiniMessages.parse("<yellow>Greetings, adventurer!</yellow>"));
        }
    }));
```

## Anti-patterns

- Spawning server-side living entities and ticking AI for static NPCs (causes server entity tick lag).
- Mutating NPC location or equipment without calling `npcModule.updateLocation(...)` or `npcModule.updateEquipment(...)`.

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`
- `java-engineering-standards`
