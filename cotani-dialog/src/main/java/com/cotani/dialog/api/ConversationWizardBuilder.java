package com.cotani.dialog.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Fluent builder for creating {@link ConversationWizard} instances.
 */
public final class ConversationWizardBuilder {

    private final List<WizardStepDefinition> steps = new ArrayList<>();

    public record WizardStepDefinition(String key, Function<WizardContext, ChatPromptBuilder<?>> promptSupplier) {}

    public record WizardContext(org.bukkit.entity.Player player, java.util.Map<String, Object> previousAnswers) {
        public java.util.Optional<Object> get(String key) {
            Objects.requireNonNull(key, "key");
            return java.util.Optional.ofNullable(previousAnswers.get(key));
        }

        public <T> java.util.Optional<T> get(String key, Class<T> type) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(type, "type");
            Object val = previousAnswers.get(key);
            if (type.isInstance(val)) {
                return java.util.Optional.of(type.cast(val));
            }
            return java.util.Optional.empty();
        }
    }

    public ConversationWizardBuilder() {}

    public <T> ConversationWizardBuilder step(String key, ChatPromptBuilder<T> promptBuilder) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(promptBuilder, "promptBuilder");
        steps.add(new WizardStepDefinition(key, _ -> promptBuilder));
        return this;
    }

    public ConversationWizardBuilder step(String key, Function<WizardContext, ChatPromptBuilder<?>> dynamicPrompt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(dynamicPrompt, "dynamicPrompt");
        steps.add(new WizardStepDefinition(key, dynamicPrompt));
        return this;
    }

    public ConversationWizard build(DialogService dialogService) {
        Objects.requireNonNull(dialogService, "dialogService");
        if (steps.isEmpty()) {
            throw new IllegalStateException("ConversationWizard must have at least one step");
        }
        return dialogService.createWizard(List.copyOf(steps));
    }
}
