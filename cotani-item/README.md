# cotani-item

Módulo de builders fluentes para criar e configurar `ItemStack`s no Paper 1.21+. Usa data components e se integra com MiniMessage para texto.

## Responsabilidade

- Fornecer `ItemStackBuilder` genérico com configuração via data components.
- Oferecer builders especializados: `ArmorBuilder` e `SkullBuilder`.
- Resolver texturas de skulls a partir de base64 ou URL.
- Integrar com `cotani-text` para nomes e lore em MiniMessage.

## Stack

- Java 21+
- Paper API 1.21+
- Adventure (via `cotani-text`)
- Caffeine (cache de texturas de skull)

## Uso básico

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
    .customName("<red>Espada Lendária")
    .lore("<gray>Dano aumentado", "<dark_gray>Item raro")
    .enchant(Enchantment.SHARPNESS, 5)
    .glow()
    .build();
```

## ArmorBuilder

```java
ItemStack helmet = ArmorBuilder.of(Material.NETHERITE_HELMET)
    .customName("<gold>Elmo Real")
    .trim(TrimMaterial.GOLD, TrimPattern.SNOUT)
    .build();
```

## SkullBuilder

```java
ItemStack skull = SkullBuilder.create()
    .player(player)
    .textureUrl("https://textures.minecraft.net/texture/...")
    .build();
```

## ItemBuilder simples

```java
ItemStack item = ItemBuilder.create(Material.EMERALD)
    .amount(64)
    .customName("<green>Moeda")
    .build();
```

## API pública

| Classe | Descrição |
|--------|-----------|
| `ItemStackBuilder<T>` | Builder base genérico para qualquer `Material`. Suporta nome, lore, encantamentos, flags, atributos, data components, comida, tool, consumable, etc. |
| `ItemBuilder` | Builder concreto para uso direto. |
| `ArmorBuilder` | Builder especializado para armaduras com suporte a `ArmorTrim`. |
| `SkullBuilder` | Builder para cabeças de jogador com perfil, textura base64 ou URL. |
| `SkullTextureResolver` | Resolve texturas de skull a partir de base64/URI. |

## Estrutura de pacotes

```text
com.cotani.item
├── ItemStackBuilder.java
├── ItemBuilder.java
├── ArmorBuilder.java
├── SkullBuilder.java
├── SkullTextureResolver.java
└── package-info.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":item"))
}
```

## Integração

- Depende de `cotani-core` e `cotani-text`.
- Usa `MiniMessages` para renderizar nomes e lore.
