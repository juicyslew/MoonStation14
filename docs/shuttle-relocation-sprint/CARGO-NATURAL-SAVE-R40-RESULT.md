# R40 — natural cargo persistence observed; continuity still fails

**Diagnostic failed; no readiness/M1 acceptance.** Preserve the used R40 world,
raw evidence, R39 failure and abandoned R38 configuration. Production/default
remains two substeps; M2–M6 remain paused. No automatic rerun or cargo recreation,
ledger repair, coordinate correction or physics change follows.

## New discriminator

Independent read-only evidence review verified receipt/raw hashes, counts and
stage boundaries. All reported observer stages had overflow=false,
fieldCaptureIncomplete=false and coverageComplete=true; missing callbacks are
not explained by lost instrumentation coverage within this admitted scope.

All three expected cargo UUIDs were eligible at readiness/release and produced
**one natural SAVE, one READ and one JOIN observation each** by the reload
timeout/terminal summary. Natural save tags contained ordinary world `Pos`, zero
own `Motion`, nonempty jug `Item` with5/6/7 water units, settled state and the
known frame orientation/storage points. Thus R39's missing cargo was not enough
to infer that ordinary serialization or reconstruction never occurred.

READ/JOIN callbacks are not proof of durable admission. Their order relative to
frame restoration and subsequent cargo motion/removal remains the useful next
question. A SAVE callback proves serializer invocation/supplied fields, not a
disk-write completion point. Post-failure entity-region absence must not be used
to contradict these direct runtime observations.

## Stage outcome

Read-only follow-up decoded the saved orientation's frame UUID as the current
frame, with storage points/world positions consistent with the saved pose.
Natural READ records retained nonempty5/6/7-unit stacks and settled state; JOIN
records were nonempty and not removed. This gives no positive evidence for a
world/storage-coordinate mismatch or empty-item rejection. The first post-join
support/motion/removal interval was not observed by R40. Settled-frame support
loss followed by a later real jug impact/spill is a hypothesis, not a diagnosis;
the local `.5` own-impact threshold and clipping policy must not be changed on
that basis. The retained world has no block-region files establishing puddles.

The filtered native selection was1/895. Compilation and fresh preparation passed;
the one test completed with failure,0 passed,0 unfinished. Existing flight,
unload, identity and content oracles were not weakened.

- Tick27: measured upward departure, rise0.984771728515625m, native Y≈19.36m/s.
- Tick28: ordinary logout/release; all three jug `shouldBeSaved()` results true,
  nonempty, not removed or passengers, exact world-ledger owners retained.
- Tick30: frame/body/three cargo actually absent; removed objects had
  `UNLOADED_TO_CHUNK`, physical chunks absent. Same-frame ordinary holding
  payload valid, upward native velocity≈18.73m/s. Natural save count1 per jug;
  read/join0 at this stage.
- Ticks30–49: quiet phase,20 observations spanning19 elapsed ticks.
- Tick50: native frame reacquired, valid handle; first observed Y velocity≈16.85.
- Tick249: three cargo lookups absent; body object and frame present. Each jug
  now had save/read/join counts1/1/1. Final lifecycle reconnect and content
  continuity verification did not complete.

The server stopped and reported all dimensions saved. The oversized failure-book
encoding error seen in R39 was not apparent in this run's shutdown; the concise
assertion and full logger witnesses remained separate. This is not a diagnosis
or fix of R26's earlier saving hang.

## Bound evidence

- Candidate `shuttle-feedback08-cargo-natural-save-20261016-r40`.
- Snapshot `4ff57058f5e234ef06b191991dc3bccd222017cab50610d631b975d6d548e3cb`.
- Frozen plan `GATE-PLAN-08-CARGO-NATURAL-SAVE-R40.json`.
- Reports root `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt `shuttle-feedback08-cargo-natural-save-20261016-r40-gate-validation.json`,
  SHA-256 `b72795062b4ec93f750dc2e7b222e1918bb6fa1a0f68b73aabd13425b5dc28cc`.
- Raw `feedback08-cargo-natural-save-r40-native.log`,319,397 bytes,
  SHA-256 `9a654fe25ffb51ecfdcd39806cc3ca062aa3f535b3bf7e2fdd00e4224179a939`.
- Raw eligibility/flight/release:1265–1271; saved frame/unload:1278–1279;
  natural summaries:1280/1284; first observed frame:1286;
  timeout and terminal summary:1301–1306; stopping/saved:1315/1328.
