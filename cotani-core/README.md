# cotani-core

Core bootstrapping, lifecycle management, and shared exceptions for the Cotani framework.

## Usage

```java
public final class MyPlugin extends JavaPlugin {
    private Cotani cotani;

    @Override
    public void onEnable() {
        cotani = Cotani.forPlugin(this)
            .with(scheduler)
            .build();
    }

    @Override
    public void onDisable() {
        cotani.close(); // Closes registered services in reverse order
    }
}
```
