# AGENTS.md Best Practices

Use `AGENTS.md` as a short repository-level instruction file.

Good `AGENTS.md` files usually include:

- project context;
- mandatory rules;
- validation commands;
- skill selection rules;
- module-specific notes;
- forbidden patterns;
- review expectations.

Avoid putting every coding rule inside `AGENTS.md` when the same rules already exist in skills. Keep detailed guidance inside skills and keep `AGENTS.md` focused.

Recommended split:

```text
AGENTS.md
  short repository instructions
  hard rules
  validation commands
  when to use each skill

.agents/skills/*/SKILL.md
  detailed rules
  examples
  review checklists
  refactoring standards
```

Avoid:

- huge context files;
- conflicting instructions;
- generic advice that does not apply to the repository;
- rules copied several times in different places;
- vague commands like "run tests" without actual command examples.

Prefer:

- specific commands like `./gradlew test`;
- short hard rules;
- concrete module notes;
- clear skill routing;
- small scoped instructions.
