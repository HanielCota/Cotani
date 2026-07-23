## Description

<!-- Describe the changes introduced by this pull request and the rationale behind them. -->

## Type of Change

- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] ✨ New feature (non-breaking change which adds functionality)
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] ♻️ Refactoring / Code Quality improvement
- [ ] 📚 Documentation update

## Checklist

- [ ] I have read the project's [AGENTS.md](file:///D:/Cotani/AGENTS.md) and coding standards.
- [ ] I have verified that `./gradlew check` passes locally without errors or warnings.
- [ ] I have applied spotless formatting using `./gradlew spotlessApply`.
- [ ] My changes follow the non-blocking async rules (no `.join()`, `.get()`, or `Thread.sleep()`).
- [ ] Main-thread safety is preserved; no Bukkit/Paper API calls are made asynchronously.
- [ ] All public APIs use defensive copies (`List.copyOf`, etc.) and `Optional` / `CompletionStage`.
- [ ] Null safety is enforced (`Objects.requireNonNull`, `@NonNull`/`@Nullable` annotations).
- [ ] I have added or updated unit tests to cover my changes.
