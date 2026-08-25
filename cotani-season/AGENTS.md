# cotani-season module instructions

- Keep season definitions and player progress immutable.
- Use `SeasonExperienceId` for every retryable experience grant.
- Register `StorageSeasonRepository.migrations()` before starting `CotaniStorage`.
- Keep reward delivery inside `RewardService`; do not manipulate economy or Bukkit objects here.
- Preserve `SeasonExperienceId` on retries and use the deterministic reward claim id for levels.
- Purge experience idempotency records only after the host's retry/unknown-outcome retention window.
- Compose repository and event stages; never block with `join()`, `get()` or sleep.
