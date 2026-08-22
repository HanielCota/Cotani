# cotani-npc

High-performance virtual player NPC management, tracking, look-at targeting, and click interactions for Paper and Folia.

## Overview

`cotani-npc` provides virtual player NPCs without ticking AI or entity overhead:

- **⚡ Zero Server-Entity Lag:** Client-side virtual NPCs rendered without ticking server entities.
- **👀 Dynamic Look-At Player:** Smooth mathematical head and body orientation towards nearby players.
- **🎨 Skin & Texture Support:** Base64 texture values and Mojang signatures.
- **⚔️ Equipment & Poses:** Armor, handheld weapons, crouching, sleeping, and sitting poses.
- **🖱️ Raycast & Click Interactions:** Detection of left/right clicks with hand information.
- **🧵 Folia & Paper Safe:** Thread-isolated tracking dispatched via `PaperTaskScheduler`.

---

## Installation

```kotlin
dependencies {
    implementation("com.cotani:cotani-npc")
}
```

---

## Bootstrap

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;
    private NpcModule npcModule;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        this.npcModule = CotaniNpcs.create(this, scheduler);

        this.cotani = Cotani.forPlugin(this)
                .with(npcModule)
                .build();
    }

    @Override
    public void onDisable() {
        if (cotani != null) {
            cotani.closeAsync();
        }
    }
}
```

---

## Usage Examples

### 1. Spawning an Interactive NPC

```java
var npc = npcModule.create(builder -> builder
    .location(spawnLocation)
    .name("<gold><bold>Merchant</bold></gold>")
    .skin(textureValue, signature)
    .lookAtPlayer(true)
    .onInteract(event -> {
        var player = event.player();
        if (event.action() == NpcInteractEvent.Action.RIGHT_CLICK) {
            shopGui.open(player);
        }
    }));
```

### 2. Updating Equipment and Location

```java
npcModule.updateEquipment(npc.id(), NpcEquipment.builder()
    .helmet(new ItemStack(Material.DIAMOND_HELMET))
    .mainHand(new ItemStack(Material.NETHERITE_SWORD))
    .build());

npcModule.updateLocation(npc.id(), newLocation);
```
