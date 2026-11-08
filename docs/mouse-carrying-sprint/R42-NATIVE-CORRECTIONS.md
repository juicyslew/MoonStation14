# r42 native corrections

r41 is finalized `needs_changes`: 78 focused JVM checks passed; the full native
suite selected 920 and failed four required tests. Readiness, client startup and
packaging were not run. Preserve the r41 receipt and logs as bound evidence.

## Shared production corrections

- `PortableBodyCarrying` retains its existing carrier owner when carrier,
  reference and key token remain unchanged. Moving a key between hands changes
  its locator, not those identities. Transfer refresh now accepts that owner's
  exact existing custody handle and operation, while still rejecting unrelated
  handles. Strict preflight and fresh post-commit snapshot checks remain intact.
- `MobStateSystem.reconcile` legitimately leaves the state attachment absent when
  the resolved state remains default Alive. Analyzer readings now use the shared,
  side-effect-free current-state query behind the existing resolved-policy gate.
  They no longer confuse absent default-state storage with unavailable policy.
  Unsupported actors remain Unknown. No analyzer-specific native health inference
  or forced state attachment was introduced.

## Fixtures

- The hand-transfer test checks retained custody and operation immediately after
  transfer, as well as original-body completion, so rejection is not diagnosed
  only after terminal retention expires.
- A world drop releases the original living mouse; it does not spawn a portable
  proxy item. The negative fixture now re-picks that living UUID through production
  selected pickup in the same tick, then checks cancellation of the original
  operation. No key-item lookup or synthetic rebind is used.
- The analyzer fixture checks absent state storage on a fresh supported mouse,
  then Alive/Critical/healing/Dead readings through typed production damage.
- The ambient squeak fixture now holds fixture chunk tickets and controls native
  wandering/gravity, attributes captured sound to its own mouse, and checks actual
  entity-tick progress. Its one-tick follow-up uses test-relative delay rather than
  accidentally passing absolute world time to `runAtTickTime`. Runtime cadence
  values and sound production are unchanged; fresh execution must still establish
  that these fixture guarantees resolve the full-suite failure.

The previously implemented cursor reconciliation, prototype Species adaptation
and detached portrait pipeline remain indexed in `ANALYZER-AND-TRANSFER.md` and
`ANALYZER-SPECIES-ADAPTATION.md`. Held-camera motion is owner-confirmed smooth and
unchanged. Fresh integration/native validation is still required; source review
and compilation alone do not certify runtime or GPU behavior.
