package com.cotani.config.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ValidationResult {

    private final List<ConfigIssue> issues = new ArrayList<>();

    public static ValidationResult valid() {
        return new ValidationResult();
    }

    public void add(ConfigIssue issue) {
        issues.add(issue);
    }

    public void merge(ValidationResult result) {
        issues.addAll(result.issues());
    }

    public boolean isValid() {
        return issues.isEmpty();
    }

    public boolean hasErrors() {
        return !issues.isEmpty();
    }

    public List<ConfigIssue> issues() {
        return List.copyOf(issues);
    }

    public String format() {
        if (issues.isEmpty()) {
            return "Config valid.";
        }
        return issues.stream()
                .map(issue -> "- " + issue.format())
                .collect(Collectors.joining(
                        System.lineSeparator(), "Config validation failed:" + System.lineSeparator(), ""));
    }

    public void log(Logger logger) {
        issues.forEach(issue -> logger.warning(issue.format()));
    }
}
