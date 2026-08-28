package com.cotani.testkit;

import java.util.Objects;
import java.util.UUID;

/** Reproduction data attached to every generated scenario failure. */
public record ScenarioContext(long seed, int iteration, String module, String operation, UUID playerId) {
    public ScenarioContext {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(playerId, "playerId");
        if (iteration < 0) {
            throw new IllegalArgumentException("iteration cannot be negative");
        }
    }

    public String description() {
        return "seed=" + seed + ", iteration=" + iteration + ", module=" + module + ", operation=" + operation
                + ", player=" + playerId;
    }

    public void verify(CheckedScenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        try {
            scenario.run();
        } catch (AssertionError failure) {
            var message = Objects.toString(failure.getMessage(), "assertion failed");
            throw new AssertionError(description() + ": " + message, failure);
        } catch (Exception failure) {
            throw new AssertionError(description() + ": unexpected failure", failure);
        }
    }

    @FunctionalInterface
    public interface CheckedScenario {
        void run() throws Exception;
    }
}
