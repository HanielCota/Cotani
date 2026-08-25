# cotani-location module instructions

- Keep homes and warps as immutable LocationPosition values; resolve World and Location only on the owning server thread.
- Persist a mutation before replacing the visible service snapshot.
- Use CotaniLocations.teleports(...) to bridge saved positions to cotani-teleport.
- Compose closeAsync() and do not block repository operations.

