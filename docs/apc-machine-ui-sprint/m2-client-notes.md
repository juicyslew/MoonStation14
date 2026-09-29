# M2 client screen notes

- `MoonStation14Client` registers `ApcScreen` for the APC menu via `RegisterMenuScreensEvent`; all Screen code stays in the client-only package.
- The screen only reads server-synchronized `ApcMenu` data: breaker state, breaker revision, and battery permille (0–1000 of the existing 1 MJ capacity). Its breaker button sends `ApcToggleRequest` with the menu container ID, menu session UUID, and displayed revision. It performs no local state mutation or prediction. The pending state only prevents duplicate clicks while awaiting a changed authoritative revision (with a bounded timeout).
- No external-power or load telemetry is present in the menu/backend snapshot. Both readouts are explicitly labeled “unavailable,” not inferred or fabricated.
- Server menu updates are presented through the existing `ContainerData` synchronization. Connected-client refresh, stale/multiple-viewer behavior and owner acceptance remain unverified here; no client was launched for this task.
- The existing `ServerClassloadingTest` allowlist does not yet recognize `ms14/power/ui/client/ApcScreen.java`, so its source-pattern audit reports the Screen's expected client imports. Updating that test is outside this task's permitted edit scope; this is an audit allowlist gap, not a common/server source dependency.
