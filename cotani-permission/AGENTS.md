# cotani-permission module instructions

- Represent subjects with UUID, permissions with PermissionNode and groups with immutable PermissionGroup values.
- Evaluate permissions through PermissionService; do not read or mutate internal maps.
- Register CotaniPermissions.migrations() before starting a storage-backed service.
- Resolve permissions asynchronously and transition back to the owning entity or global thread before touching Bukkit.

