# cotani-cleanup instructions

- Keep `CleanupPolicy` as an explicit allow-list. Do not add a catch-all entity deletion mode.
- Never delete players, living mobs, NPCs, armor stands or named entities through the default targets.
- Keep the core API Bukkit-free; Paper access belongs in `com.cotani.cleanup.paper`.
- Capture only UUIDs, world IDs, chunk coordinates and immutable metadata before async composition.
- Use the global scheduler only to capture immutable loaded-world/chunk coordinates; inspect chunks on their owning region threads.
- Remove through the entity scheduler so movement between scan and removal cannot cross region ownership.
- Revalidate each candidate immediately before removal because entities may move, age, or become protected after scanning.
- Keep preview side-effect free and make execute reports observable through domain events.
- Bound candidates and pending operations. Do not dispatch an unbounded task per entity.
- Reuse `CleanupProtection` for plugin-owned markers and region-derived immutable protection decisions.
- Do not use `join`, `get`, `sleep` or implicit async executors in application code.
