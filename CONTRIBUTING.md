# Contributing to Cotani

Thank you for your interest in contributing to Cotani! This document provides guidelines and steps for contributing to the project.

## Code Standards & Principles

Cotani is built on strict Java engineering standards:

1. **Clean Architecture & SRP**: Modules are strictly decoupled. API packages must never depend on implementation details.
2. **Main-Thread Safety**: Never call Bukkit/Paper APIs asynchronously unless explicitly thread-safe.
3. **Non-Blocking Async**: Never use `.join()`, `.get()`, or `Thread.sleep()`. Compose async flows with `CompletionStage`.
4. **Null Safety**: Annotate public APIs and check required inputs with `Objects.requireNonNull`. Never return `null`.
5. **Imutability**: Return unmodifiable defensive copies (`List.copyOf`, `Set.copyOf`, `Map.copyOf`).

For full design rules, please refer to [AGENTS.md](AGENTS.md).

## Local Development Setup

### Prerequisites

- Java 25 JDK
- Git

### Build & Validate

Before submitting any code changes, run the full validation suite locally:

```bash
# Apply Spotless code formatting
./gradlew spotlessApply

# Run all checks, spotless format validation, module architecture checks & unit tests
./gradlew check
```

For documentation-only changes, also verify the generated API reference and the compile-checked examples:

```bash
./gradlew aggregateJavadoc
./gradlew :examples:compileJava
```

Keep module READMEs aligned with the public `api` packages. Examples must use `CompletionStage` composition, explicit
thread transitions and immutable identifiers; do not document `join()`, `get()`, `Thread.sleep(...)` or live Bukkit
objects captured in async callbacks. Cross-cutting references are indexed in [`docs/README.md`](docs/README.md).

Database-backed documentation and examples should be exercised with:

```bash
./gradlew integrationTest
```

## Submitting Pull Requests

1. Fork the repository and create a feature branch (`git checkout -b feature/my-feature`).
2. Make small, cohesive commits with clear commit messages following conventional commits.
3. Ensure `./gradlew check` passes cleanly without format or test violations.
4. Push your branch and submit a Pull Request.
5. Fill out the PR template checklist completely.
