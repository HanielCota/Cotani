# cotani-text

Utility module for Adventure and MiniMessage. Handles component parsing, legacy-to-minimessage styling conversions, placeholders, and messaging senders.

## Usage

```java
Component parsed = MiniMessages.parse("<green>Hello, <player>!</green>");
AudienceMessages.sendMessage(player, "<yellow>Welcome back!");
```
