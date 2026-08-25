# cotani-audit-storage module instructions

- Register CotaniAuditStorages.migrations() before starting CotaniStorage.
- Create the adapter only over a started storage instance.
- Keep SQL mapping inside StorageAuditRepository; application services should depend on AuditRepository.
- Use bounded AuditQuery filters and compose closeAsync() during shutdown.

