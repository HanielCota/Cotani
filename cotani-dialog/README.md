# cotani-dialog

Non-blocking reactive dialog and player input prompt module for Paper and Folia plugins providing Chat prompts, Anvil GUIs, and multi-step conversational Wizards.

## Overview

`cotani-dialog` eliminates tedious boilerplate for capturing and validating player text input:

- **💬 Async Chat Prompts:** Intercept player responses in chat before standard broadcast with typed parsing, retry limits, and cancel keywords.
- **🔨 Anvil Input GUI:** Open virtual Anvil interfaces for renaming and text input with complete anti-exploit click and drag protection.
- **🧙 Multi-Step Wizards:** Chain sequential questions into a fluent wizard where each step has access to previously collected answers.
- **🧵 Folia & Paper Safe:** Timeouts and thread transitions to player entity threads are safely dispatched through `PaperTaskScheduler`.
- **🛡️ Auto-Cleanup:** Automatically cancels and releases active prompts on `PlayerQuitEvent`, prompt overriding, and plugin shutdown.

---

## Installation

Add `cotani-dialog` to your build script:

```kotlin
dependencies {
    implementation("com.cotani:cotani-dialog")
}
```

---

## Bootstrap

Register the module once in your plugin's `onEnable`:

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;
    private DialogService dialogs;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        this.dialogs = CotaniDialogs.create(this, scheduler);

        this.cotani = Cotani.forPlugin(this)
                .with(dialogs)
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

### 1. Typed Chat Prompt with Validation

```java
dialogs.chat()
    .message("<yellow>Enter the deposit amount (or 'cancel'):</yellow>")
    .timeout(Duration.ofSeconds(30))
    .cancelKeywords("cancel", "sair", "exit")
    .maxAttempts(3)
    .parser(raw -> {
        try {
            int amount = Integer.parseInt(raw);
            return amount > 0 ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    })
    .onInvalidInput(Component.text("Please enter a valid positive integer!"))
    .build(dialogs)
    .start(player)
    .thenAccept(result -> {
        result.ifSuccess(amount -> player.sendMessage(Component.text("Depositing " + amount + " coins...")));
        result.ifCancelled(reason -> player.sendMessage(Component.text("Cancelled: " + reason)));
    });
```

### 2. Anvil GUI Prompt

```java
dialogs.anvil()
    .title(Component.text("Rename Clan"))
    .initialText("Knights")
    .timeout(Duration.ofMinutes(1))
    .build(dialogs)
    .open(player)
    .thenAccept(result -> {
        result.ifSuccess(name -> player.sendMessage(Component.text("New clan name: " + name)));
        result.ifCancelled(reason -> player.sendMessage(Component.text("Anvil input cancelled.")));
    });
```

### 3. Multi-Step Conversational Wizard

```java
var wizard = dialogs.wizard()
    .step("name", dialogs.chat().message("<aqua>Step 1: Enter Clan Name</aqua>").parser(Optional::of))
    .step("tag", dialogs.chat().message("<aqua>Step 2: Enter 3-letter Clan Tag</aqua>")
        .parser(tag -> tag.length() == 3 ? Optional.of(tag.toUpperCase()) : Optional.empty()))
    .build(dialogs);

wizard.start(player).thenAccept(result -> {
    result.ifSuccess(answers -> {
        String name = (String) answers.get("name");
        String tag = (String) answers.get("tag");
        player.sendMessage(Component.text("Clan [" + tag + "] " + name + " successfully created!"));
    });
});
```

---

## API Summary

| Interface | Role |
| :--- | :--- |
| [`CotaniDialogs`](src/main/java/com/cotani/dialog/CotaniDialogs.java) | Entrypoint factory for creating `DialogService` |
| [`DialogService`](src/main/java/com/cotani/dialog/api/DialogService.java) | Main service interface managing active prompt sessions |
| [`PromptResult`](src/main/java/com/cotani/dialog/api/PromptResult.java) | Result outcome model (`Success`, `Cancelled`, `Failure`) |
| [`CancelReason`](src/main/java/com/cotani/dialog/api/CancelReason.java) | Cancellation reason enumeration |
| [`ChatPrompt`](src/main/java/com/cotani/dialog/api/ChatPrompt.java) | Interactive chat prompt contract |
| [`AnvilPrompt`](src/main/java/com/cotani/dialog/api/AnvilPrompt.java) | Anvil GUI input contract |
| [`ConversationWizard`](src/main/java/com/cotani/dialog/api/ConversationWizard.java) | Sequential multi-step questionnaire wizard |
