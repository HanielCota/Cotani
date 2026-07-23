# Contributing to Cotani

Thank you for your interest in contributing to Cotani! This document provides guidelines and steps for contributing to the project.

## Code Standards & Principles

Cotani is built on strict Java engineering standards:

1. **Clean Architecture & SRP**: Modules are strictly decoupled. API packages must never depend on implementation details.
2. **Main-Thread Safety**: Never call Bukkit/Paper APIs asynchronously unless explicitly thread-safe.
3. **Non-Blocking Async**: Never use `.join()`, `.get()`, or `Thread.sleep()`. Compose async flows with `CompletionStage`.
4. **Null Safety**: Annotate public APIs and check required inputs with `Objects.requireNonNull`. Never return `null`.
5. **Imutability**: Return unmodifiable defensive copies (`List.copyOf`, `Set.copyOf`, `Map.copyOf`).

For full design rules, please refer to [AGENTS.md](file:///D:/Cotani/AGENTS.md).

## Local Development Setup

### Prerequisites

- Java 21 or Java 25 JDK
- Git

### Build & Validate

Before submitting any code changes, run the full validation suite locally:

```bash
# Apply Spotless code formatting
./gradlew spotlessApply

# Run all checks, spotless format validation, module architecture checks & unit tests
./gradlew check
```

## Submitting Pull Requests

1. Fork the repository and create a feature branch (`git checkout -b feature/my-feature`).
2. Make small, cohesive commits with clear commit messages following conventional commits.
3. Ensure `./gradlew check` passes cleanly without format or test violations.
4. Push your branch and submit a Pull Request.
5. Fill out the PR template checklist completely.
