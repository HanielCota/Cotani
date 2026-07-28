# cotani-gui

Declarative, reactive and anti-dupe-safe GUI engine for Paper and Folia.

## Setup

Register the module once in `onEnable`:

```java
Cotani cotani = Cotani.forPlugin(this)
    .with(CotaniGuiModule.create(plugin)) // 100ms default click debounce
    .build();
```

## Usage

```java
public final class ProfileGui {

    public static void open(Player player, UserProfile profile) {
        var fly = State.of(profile.hasFlyEnabled());
        var tell = State.of(profile.hasTellEnabled());

        GuiWindow.panel("Seu Perfil")
            .rows(3)
            .structure(
                "# # # # # # # # #",
                "# F . T . . . H #",
                "# # # # # # # # #"
            )
            .border(Material.GRAY_STAINED_GLASS_PANE)
            .bindToggle('F', fly, Material.FEATHER, "Modo Voo", profile::setFly)
            .bindToggle('T', tell, Material.PAPER, "Receber Mensagens", profile::setTell)
            .bind('H', Items.head(player, "<gold>" + player.getName(),
                "<gray>Saldo: <yellow>$" + profile.getBalance()))
            .onClose(ctx -> profileRepository.saveAsync(profile))
            .open(player);
    }
}
```

## Threading contract

- `GuiWindow.open(player)` must run on the thread that owns the player (main thread on Paper, entity
  region thread on Folia).
- Bound `Property` mutations trigger synchronous re-renders; mutate them on the viewer-owning thread.
- Click dispatch, debounce and anti-dupe cancellation are handled centrally by `AntiExploitGuard`.
