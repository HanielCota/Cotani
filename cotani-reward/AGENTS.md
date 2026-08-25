# cotani-reward module instructions

- Keep reward definitions, claims and grants immutable.
- Use a stable RewardClaimId whenever delivery can be retried.
- Persist the claim before settlement; acknowledge with markSettledAsync(...) only after all handlers complete.
- Recover pending claims with bounded pendingClaimsAsync(limit) and compose closeAsync() into shutdown.

