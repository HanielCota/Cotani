package com.cotani.dialog.impl;

import com.cotani.api.InternalApi;
import com.cotani.dialog.api.ConversationWizard;
import com.cotani.dialog.api.ConversationWizardBuilder.WizardContext;
import com.cotani.dialog.api.ConversationWizardBuilder.WizardStepDefinition;
import com.cotani.dialog.api.PromptResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

@InternalApi
public final class DefaultConversationWizard implements ConversationWizard {

    private final List<WizardStepDefinition> steps;
    private final com.cotani.dialog.api.DialogService dialogService;

    public DefaultConversationWizard(
            List<WizardStepDefinition> steps, com.cotani.dialog.api.DialogService dialogService) {
        this.steps = Objects.requireNonNull(steps, "steps");
        this.dialogService = Objects.requireNonNull(dialogService, "dialogService");
    }

    @Override
    public CompletionStage<PromptResult<Map<String, Object>>> start(Player player) {
        Objects.requireNonNull(player, "player");
        var future = new CompletableFuture<PromptResult<Map<String, Object>>>();
        var answers = new LinkedHashMap<String, Object>();

        runStep(player, 0, answers, future);

        return future;
    }

    private void runStep(
            Player player,
            int stepIndex,
            Map<String, Object> answers,
            CompletableFuture<PromptResult<Map<String, Object>>> future) {

        if (stepIndex >= steps.size()) {
            future.complete(PromptResult.success(Collections.unmodifiableMap(answers)));
            return;
        }

        var stepDef = steps.get(stepIndex);
        var context = new WizardContext(player, Collections.unmodifiableMap(answers));

        try {
            var promptBuilder = Objects.requireNonNull(
                    stepDef.promptSupplier().apply(context),
                    () -> "promptSupplier returned null for step: " + stepDef.key());
            var prompt = promptBuilder.build(dialogService);

            prompt.start(player).whenComplete((result, error) -> {
                if (error != null) {
                    future.complete(PromptResult.error(error));
                    return;
                }
                if (result == null) {
                    future.complete(PromptResult.error(new NullPointerException("Prompt result was null")));
                    return;
                }

                switch (result) {
                    case PromptResult.Cancelled<?>(var reason) -> future.complete(PromptResult.cancelled(reason));
                    case PromptResult.Failure<?>(var cause) -> future.complete(PromptResult.error(cause));
                    case PromptResult.Success<?>(var value) -> {
                        answers.put(stepDef.key(), value);
                        runStep(player, stepIndex + 1, answers, future);
                    }
                }
            });
        } catch (Throwable t) {
            future.complete(PromptResult.error(t));
        }
    }
}
