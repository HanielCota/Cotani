# cotani-text

Módulo utilitário para trabalhar com texto no ecossistema Adventure: MiniMessage, placeholders, serialização entre formatos e envio para audiências.

## Responsabilidade

- Simplificar parsing e serialização de MiniMessage.
- Fornecer placeholders (`TagResolver`) de forma fluente.
- Converter entre MiniMessage, legacy (`&` e `§`), plain text e JSON.
- Enviar mensagens, action bar, header/footer de player list sem boilerplate.
- Servir de base para `cotani-config` e `cotani-item`.

## Stack

- Java 21+
- Adventure API (via Paper)
- MiniMessage
- JSpecify

## Uso básico

### MiniMessage

```java
Component component = MiniMessages.parse("<green>Olá, <player>!</green>");
Component escaped = MiniMessages.escape("<red>não vai ser vermelho</red>");
String back = MiniMessages.serialize(component);
```

### Placeholders

```java
TagResolver resolver = Placeholders.unparsed("player", player.getName());
Component msg = MiniMessages.parse("<green>Olá, <player>!</green>", resolver);
```

### Conversões

```java
Component fromLegacy = ComponentTexts.fromLegacy("&aTexto verde");
String mini = ComponentTexts.toMiniMessage(component);
String json = ComponentTexts.toJson(component);
Component fromJson = ComponentTexts.fromJson(json);
```

### Envio para audiências

```java
AudienceMessages.sendMessage(player, "<green>Saldo: <balance>",
    Placeholders.component("balance", Component.text("R$ 100")));

AudienceMessages.sendActionBar(player, "<yellow>Carregando...");
```

## API pública

| Classe | Descrição |
|--------|-----------|
| `MiniMessages` | Parse/serialize/escape/strip de MiniMessage. |
| `Placeholders` | Fábrica fluente de `TagResolver`s (component, unparsed, parsed, styling, number, date, choice, booleanChoice, joining). |
| `ComponentTexts` | Criação e conversão de `Component` entre formatos. |
| `AudienceMessages` | Envio de MiniMessage para `Audience` (mensagem, action bar, player list header/footer). |
| `ComponentSerializers` | Acesso aos serializers Adventure subjacentes (uso interno). |

## Estrutura de pacotes

```text
com.cotani.text
├── MiniMessages.java
├── Placeholders.java
├── ComponentTexts.java
├── AudienceMessages.java
├── ComponentSerializers.java
└── package-info.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":text"))
}
```

## Integração

- `cotani-config` usa `MiniMessages` para ler `Component` de arquivos YAML.
- `cotani-item` usa `MiniMessages` para nomes e lore de itens.
