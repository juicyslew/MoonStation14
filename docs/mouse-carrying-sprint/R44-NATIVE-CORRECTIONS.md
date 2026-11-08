# r44 native gate correction scope

r44 is finalized `needs_changes`: 929 native tests ran, 25 required tests failed,
and no all-required-passed marker was present. Its full JVM check passed 3,332
tests; that does not compensate for the native failure. Preserve its bound reports
and failed run directory.

## Independent causes

- **Persisted lifecycle world:** the reused `build/gametest-tags-fixture` contains
  an `OFFLINE` reservation from October 12. Startup correctly classified its
  nonempty store `DEFERRED`, then production first-join admission disconnected new
  embedded fixture accounts. Closed-channel errors are downstream failures, not
  passing gameplay evidence. The r45 gate uses a fresh isolated run directory;
  production recovery/admission policy is unchanged. R43's separate directory
  started `UNINITIALIZED` with zero profiles.
- **Pressure cadence:** the ceiling fixture repeated the same due timestamp.
  Its old native-alive guard masked this invalid stop-check after the first hit
  killed the pig. Distinct due slots now exercise actual cap-stop and
  healing-reopen ledger behavior while retaining corpse pressure eligibility.
- **Posture audio Sheep:** the GameTest-only inherited Human profile intentionally
  binds Sheep. Explicit enrollment requires matching host ownership, so simply
  removing that binding breaks the existing live prototype-consumer test. The
  complete-appearance guard must remain strict; native Sheep presentation support
  belongs in the shared snapshot owner, not an analyzer branch or test-ID exemption.
- **Trusted living pickup:** automatic loose-item candidates must stay item-only.
  An explicit living-body UID must reach shared selected admission instead of an
  item-only search; otherwise the reentrant authority callback never executes and
  the ownership fixture does not exercise compare-and-commit rejection.

## Required fresh evidence

Use `mouse-character-r45-gate.json`. Require every required native test to pass,
including the original r44 player-backed, pressure, portrait and ownership
failures. The gate also retains actual throw-to-pull, alive/critical/dead pickup
and pull, corpse landing/native damage/cancellation, and pressure/blast coverage.
Full readiness, startup and packaging remain mandatory. These notes do not claim
that the corrected native suite has passed or that owner acceptance occurred.

## R45 result and R46 corrections

R45 remained `needs_changes`: 930 native tests ran, three required tests failed.
The persisted-world and Sheep/pickup corrections did not establish a full pass.

- The pressure failure was **not** solved by cadence isolation: the shared
  damage-preview query returned an empty map before reading stored damage on dead
  bodies. R46 preserves the authoritative stored ledger, while still refusing to
  import zero corpse health as a fresh vanilla baseline.
- The pull negative control was a Villager, which legitimately resolves Human's
  `Pullable`. The replacement control verifies its resolved absence of that
  component before asserting denial; production pull policy stays unchanged.
- The readiness helper checked expiration before evaluating readiness on the
  deadline tick. R46 corrects the inclusive boundary and adds direct boundary
  fixture coverage; mouse AI/movement assertions remain intact.

R46 must pass the full gate in fresh lifecycle isolation. Preserve both failed
revisions' artifacts; no acceptance, deployment or cleanup is authorized.

## R46 result and R47 correction

R46 remained `needs_changes`: one of 932 required native tests failed. The
remaining overload fixture had a clean/indexed graph but an unknown HV component.
`LoadedPowerGraph` tracks the chunks of topology-compatible one-block neighbor
candidates; any unloaded candidate frontier keeps the component unknown. The
fixture's zero-padding tickets omitted the preceding chunk when its first HV host
landed exactly on a chunk boundary. R47 tickets the complete bounded neighbor halo
using `FixtureChunkTickets`, then retains normal asynchronous authority checks.
Production graph fail-closed behavior and the timeout are unchanged.

Fixture readiness checks every chunk intersecting the one-block halo with
non-loading `getChunkNow` lookups, and waits for actual first-lamp delivery before
advancing phases. Merely leaving visual state `UNKNOWN` is insufficient: `LACK`
does not prove that the initial load has been delivered.

The initially absent second lamp is intentional: establish the first 12 kW load,
then place the second to begin the real 24 kW overload interval. It was not a lost
block and must not be moved earlier to disguise the failure. R47 retains trip,
darkness and manual reclose assertions. Readiness/startup/packaging had not run
after r46's native failure and remain mandatory in the fresh full gate.
