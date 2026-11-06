# R30 stock-only control: support timing and plot-owned residency

R28/R29 never reached flight; their original fixtures, logs, receipts and frozen
plans remain preserved. See their `*-RESULT.md` records. R30 corrects local
test-harness premises without modifying Sable or production/owner settings.

## Corrections

- Source hull starts **at** support-surface y=−47, not one block above it.
  After public assembly returns, exact owner tracking and pad authoring occur in
  the same server turn, before asynchronous readiness waits. This follows the
  working R16 stock-assembly support premise; there is no pose correction.
- Finite state, hull conservation and coverage remain required while settling.
  The unchanged near-identity tolerance (1e−6) is checked at grounded admission,
  after ten naturally settled samples—not against a transient first contact.
  Current support/linear/angular criteria remain unchanged.
- Vanilla forced tickets and FULL/ENTITY-TICKING checks apply **only** to the
  bounded physical-world pad. Their insertion rejects out-of-pad chunks.
  Virtual storage readiness instead uses the exact live public container owner,
  `SubLevelContainer.getPlot(globalChunk)`, `LevelPlot.contains`, `toLocal` and
  plot-owned `getChunk`. Missing chunks fail bounded readiness; the harness does
  not create replacements or force the whole reserved plot.

This follows pinned stock `SubLevelContainer`, `LevelPlot` and
`SubLevelAssemblyHelper` ownership at `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`,
under `common/src/main/java/dev/ryanhcode/sable/`. Public assembly creates plot
chunks independently of ordinary world-chunk distance tracking. The previous
vanilla storage-ticket usage was locally mismatched. Its coordinates were very
large; the harness did **not** force millions of chunks. Whether its small set of
virtual-coordinate tickets caused R29's sampled distance-update stall remains
unproved. Do not change older fixtures or claim their hang fixed from this alone.

## Unchanged controls

Same 452-cell vanilla shell, 451 stone plus one glass; pre/post resolved
properties and actual tracker sum (903 in R29) must agree. Same public stock
assembly, separate 20/80 m/s upward velocity deltas and actual two substeps.
Authenticated artifact/version/MS14-exclusion guards, shared SAT/fingerprint/
failure tracker, first-witness/history/bounded follow-through, current-contact
settling and owner-first cleanup-before-success remain. The bounded exact-server
shutdown sampler is unchanged. No velocity/rotation/gravity manipulation, engine
hooks, material substitution or production mitigation is added.

R29 already proved the unchanged root shared-capture extraction compiles; do not
duplicate that admitted check. R30's changed standalone fixture passed focused
`compileJava processResources`. Independent static physical/ownership review
passed; neither compilation nor review establishes a native outcome.

## Fresh check and interpretation

Use only `tooling/stock-sable-control/build/gametest-shuttle-feedback-08-stock-only-r30`
and raw `build/agent-workflow/shuttle-relocation-sprint/reports/feedback08-stock-only-r30-native.log`.
Expected full **control catalogue**: 2/2, not MS14's suite. Require guard execution,
isolated authenticated runtime, actual world/plot residency, composition and
grounded admission before interpreting any launched trajectory. Report failure
assertions honestly, including bounded histories, and evaluate save/exit separately.

A valid stock-only penetration means MS14 is unnecessary for that vanilla-shell
symptom, not an engine defect or violation of a documented speed guarantee.
Stock-only passes require the same vanilla experiment with MS14 present before
integration attribution. Setup/coverage failures remain inconclusive. This is not
the R16 material/mass composition, a cargo/body continuity proof, readiness or
M1 acceptance. Clean shutdown would be non-reproduction of R29, not unique causal
proof for either harness change or an explanation of R26.
