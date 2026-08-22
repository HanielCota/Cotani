package com.cotani.dialog;

import com.cotani.dialog.api.AnvilPromptBuilder;
import com.cotani.dialog.api.ChatPromptBuilder;
import com.cotani.dialog.api.ConversationWizardBuilder;
import com.cotani.dialog.api.DialogService;
import com.cotani.dialog.impl.DefaultDialogService;
import com.cotani.task.CotaniTasks;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Entrypoint factory for the {@code cotani-dialog} module.
 */
public final class CotaniDialogs {

    private CotaniDialogs() {}

    /**
     * Creates and registers a new {@link DialogService} for the given plugin.
     *
     * @param plugin owning plugin
     * @return new dialog service instance
     */
    public static DialogService create(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PaperTaskScheduler scheduler = CotaniTasks.create(plugin);
        return create(plugin, scheduler);
    }

    /**
     * Creates and registers a new {@link DialogService} using the given scheduler.
     *
     * @param plugin owning plugin
     * @param scheduler task scheduler
     * @return new dialog service instance
     */
    public static DialogService create(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        return new DefaultDialogService(plugin, scheduler);
    }

    /**
     * Creates a new chat prompt builder.
     *
     * @param <T> value type
     * @return builder
     */
    public static <T> ChatPromptBuilder<T> chat() {
        return new ChatPromptBuilder<>();
    }

    /**
     * Creates a new anvil prompt builder.
     *
     * @return builder
     */
    public static AnvilPromptBuilder anvil() {
        return new AnvilPromptBuilder();
    }

    /**
     * Creates a new conversation wizard builder.
     *
     * @return builder
     */
    public static ConversationWizardBuilder wizard() {
        return new ConversationWizardBuilder();
    }
}
