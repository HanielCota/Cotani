# cotani-audit module instructions

- Keep audit entries immutable and bounded; use AuditQuery limits and cursors for history screens.
- Store actor and target identifiers as plain values. Never retain or serialize live Bukkit objects.
- recordAsync writes are ordered. Treat a repository failure as terminal for the service and recreate it after repair.
- Compose closeAsync() into the owning plugin lifecycle.

