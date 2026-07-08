# cotani-item

Fluent builders for creating and configuring `ItemStack` instances, player heads with custom textures (base64/URL), and armor trims.

## Usage

```java
ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
    .customName("<red>Legendary Sword")
    .lore("<gray>Increased damage", "<dark_gray>Rare Item")
    .glow()
    .build();
```
