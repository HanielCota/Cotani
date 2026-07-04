# cotani-item

## Scope

Fluent builders for `ItemStack` using Paper 1.21+ data components.

## Hard rules

1. Use `ItemBuilder.of(Material)`, `ArmorBuilder.of(Material)` or `SkullBuilder.create()` instead of direct `ItemStack` mutation.
2. Text inputs (name, lore) must be MiniMessage strings; they are parsed through `cotani-text`.
3. `build()` returns a cloned `ItemStack`; the builder remains reusable.
4. Validate armor material in `ArmorBuilder.of(...)`; it throws for non-armor materials.

## Patterns

### Basic item

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
    .customName("<red>Espada Lendária")
    .lore("<gray>Dano aumentado")
    .enchant(Enchantment.SHARPNESS, 5)
    .glow()
    .build();
```

### Armor

```java
ItemStack helmet = ArmorBuilder.of(Material.NETHERITE_HELMET)
    .customName("<gold>Elmo Real")
    .trim(TrimMaterial.GOLD, TrimPattern.SNOUT)
    .build();
```

### Skull texture

```java
ItemStack skull = SkullBuilder.create()
    .textureUrl("https://textures.minecraft.net/texture/...")
    .build();
```

## Anti-patterns

- Mutating `ItemStack` directly with deprecated `ItemMeta` setters.
- Creating new builders for every lore line instead of using varargs `lore(...)`.
- Passing legacy color codes instead of MiniMessage.

## Related skills

- `java-engineering-standards`
