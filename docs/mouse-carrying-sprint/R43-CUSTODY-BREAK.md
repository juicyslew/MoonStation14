# r43 custody continuity and real drop cancellation

r42 is finalized `needs_changes`: full native validation selected 920 and failed
three required tests. Readiness, client startup and packaging did not run.
Preserve its saved receipt and native log.

## Corrected invariants

`PreparedRefresh.apply` must **not** check currency of the old hand locator after
commit. The old locator is expected to be stale. The r42 retained-owner adjustment
regressed this rule by adding `custody.isCurrent()` there. It is removed again.
Preflight still requires the old snapshot current; apply validates exact custody,
body, carrier, reference and active operation plus the registered next owner and
a newly captured current snapshot. The original callback custody is reused.

The transfer fixture explicitly verifies the old snapshot is current before
commit and stale afterward, while the retained custody is fresh and current.
Its bounded observation uses the original start tick, checks operation continuity
through the deadline and observes completion after the server-post timer phase;
it no longer assumes a level callback at exactly 100 ticks runs after that phase.

Manual portable-body drop now invokes `PortableBodyEscapeService.onDropped` only
after the canonical release transaction succeeds. It reuses timed cancellation,
filtered by escape kind and body UUID. A committed custody break therefore cannot
be erased by re-pickup before the next timer tick. Denied release and physical
hand transfer do not emit this signal. Escape completion does not self-cancel:
its release callback is separate from the manual-drop entry path.

The negative fixture verifies the old attempt is already cancelled immediately
after the real world drop, before selected pickup of the original living UUID in
the same tick, and remains cancelled afterward.

## Boundaries

These source fixes and compiled fixtures still require fresh native validation.
The unrelated required airlock corpse-capture fixture repositions its corpse
after the typed death transition and asserts loaded, physical doorway overlap
before unsafe close. Retained-dead capture and paid-reopen assertions remain.
Fresh execution must establish whether these preconditions resolve the failure.
Analyzer,
Species adaptation, cursor reconciliation and owner-confirmed smooth camera remain
as indexed in `ANALYZER-AND-TRANSFER.md` and `R42-NATIVE-CORRECTIONS.md`.
Connected cursor/portrait rendering and physical input remain manual acceptance.
