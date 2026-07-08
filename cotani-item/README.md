# cotani-item

Fluent builders for `ItemStack` using Paper 1.21+ data components.

## Overview

`cotani-item` offers a modern, fluent builder API to construct and modify `ItemStack` objects in Paper 1.21+. Instead of dealing with the verbose and error-prone `ItemMeta` API or using outdated legacy color codes, it exposes builders for weapons, armor, and player skulls, parsing text inputs directly via MiniMessage.

## Features

- **Modern Paper Data Components**: Leverages Paper 1.21+ API.
- **Fluent Builder APIs**: Chain configurations like enchants, custom names, lore, trims, and skull textures.
- **Skull Texture Resolving**: Create player heads with base64 skin textures or Mojang texture URLs seamlessly.
- **Type-Safe Armor Builder**: `ArmorBuilder` validates materials to ensure only valid armor items are trimmed.
- **MiniMessage Integration**: All text arguments are parsed through the MiniMessage formatting engine.

## Usage

### 1. Constructing a Basic Weapon/Item

Define properties, name, lore, and enchantments using MiniMessage:

```java
import com.cotani.item.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
    .customName("<red>Espada Lendária")
    .lore("<gray>Dano base aumentado", "<dark_gray>Item exclusivo")
    .enchant(Enchantment.SHARPNESS, 5)
    .glow() // Apply visual enchantment glow effect without actual level metadata
    .build();
```

### 2. Customizing Armor Trims

Apply custom armor trims and patterns:

```java
import com.cotani.item.ArmorBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

ItemStack helmet = ArmorBuilder.of(Material.NETHERITE_HELMET)
    .customName("<gold>Elmo Real")
    .trim(TrimMaterial.GOLD, TrimPattern.SNOUT)
    .build();
```

### 3. Setting Skull Skin Textures

Build player heads from skin texture URLs:

```java
import com.cotani.item.SkullBuilder;
import org.bukkit.inventory.ItemStack;

ItemStack skull = SkullBuilder.create()
    .textureUrl("https://textures.minecraft.net/texture/a5525bc828d17961d76442657e23297a7d4a6f23e445037d400e000000000000")
    .build();
```

## Hard Rules & Best Practices

1. **Strict Builder Usage**: Use `ItemBuilder.of(Material)`, `ArmorBuilder.of(Material)`, or `SkullBuilder.create()` instead of direct manual `ItemStack` and `ItemMeta` mutations.
2. **Text Formats**: All custom names, lore lists, and text inputs must be MiniMessage strings. They are internally parsed through the `cotani-text` utilities.
3. **Immutability & Reusability**: The `build()` method returns a cloned instance of the generated `ItemStack`. The builder instance itself remains reusable for subsequent item modifications.
4. **Armor Type Validation**: `ArmorBuilder` enforces checks on material inputs at instantiation. Supplying invalid materials (e.g. `Material.DIRT`) raises an `IllegalArgumentException`.

## Anti-Patterns

- Directly mutating items using legacy, deprecated `ItemMeta` methods.
- Passing legacy color indicators (like `§` or `&`) to names or lore (use MiniMessage syntax instead).
- Creating new builder instances for every single line of lore instead of using the varargs `lore(String...)` method.
